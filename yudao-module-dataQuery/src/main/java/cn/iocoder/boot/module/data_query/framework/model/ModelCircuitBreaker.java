package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelCircuitState;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.service.AiModelCircuitStateService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 模型熔断器（Redis 三态）
 *
 * 熔断与降级的区别：降级管「这一次请求换模型」，熔断管「接下来一段时间别再打这个模型」。
 * 下游持续故障时若不做熔断，每个请求仍会白等一个 timeout、占住并发槽，重试再把流量放大，
 * 最终整个问答端雪崩。熔断的本质是快速失败，把故障关在模型层。
 *
 * 架构约束：运行态以 Redis 为准（多实例共享、原子 CAS），DB 表 ai_model_circuit_state
 * 只在「状态跃迁」时写一次快照，用于审计 / 管理端展示 / 重启恢复，绝不每次调用都 UPDATE。
 *
 * 统计窗口是「按 windowSeconds 对齐的固定窗口」，实现简单但有临界突刺；
 * 若要严格滑动窗口，可把计数换成 Redis ZSet + 按 score 清理，后续替换本类内部实现即可。
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    public static final String STATE_CLOSED = "CLOSED";
    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_HALF_OPEN = "HALF_OPEN";

    private static final String KEY_PREFIX = "ai:model:circuit:%s:";
    private static final String SUFFIX_STATE = "state";
    private static final String SUFFIX_OPEN_UNTIL = "openUntil";
    private static final String SUFFIX_PROBE = "probe";
    private static final String SUFFIX_PROBE_OK = "probeOk";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private AiModelCircuitStateService aiModelCircuitStateService;

    @Resource
    private ModelRateLimiter modelRateLimiter;

    /**
     * 熔断维度键：优先用管理端自定义的 circuit_key，否则按 provider_id:model_config_id
     */
    public static String circuitKey(AiModelConfig config) {
        String base = StringUtils.hasText(config.getCircuitKey())
                ? config.getCircuitKey()
                : config.getProviderId() + ":" + config.getId();
        // 熔断 / 限流的 Redis key 必须带租户前缀：每个租户用自己的供应商账号，
        // 若共用同一个 key，A 把模型打挂会连坐熔断 B，B 也会消耗 A 的 QPS 配额。
        // DB 快照表 ai_model_circuit_state 是租户私有表，circuit_key 存同一个带前缀值即可对齐；
        // 管理端 reset-circuit 直接传 route-preview 返回的这个 key。
        Long tenantId = TenantContextHolder.getTenantId();
        return (tenantId == null ? 0L : tenantId) + ":" + base;
    }

    public boolean isEnabled(AiModelConfig config) {
        return config.getCircuitEnabled() != null && config.getCircuitEnabled() == 1;
    }

    /**
     * 是否放行本次请求。CLOSED 放行；OPEN 未到期拒绝，到期后 CAS 转 HALF_OPEN 放探测；
     * HALF_OPEN 只放行有限个探测请求，其余快速失败。
     */
    public boolean allowRequest(AiModelConfig config) {
        if (!isEnabled(config)) {
            return true;
        }
        String key = circuitKey(config);
        try {
            RBucket<String> stateBucket = stateBucket(key);
            String state = stateBucket.get();
            if (!StringUtils.hasText(state)) {
                stateBucket.set(STATE_CLOSED);
                return true;
            }
            if (STATE_OPEN.equals(state)) {
                RBucket<Long> untilBucket = redissonClient.getBucket(redisKey(key, SUFFIX_OPEN_UNTIL));
                Long until = untilBucket.get();
                if (until != null && System.currentTimeMillis() < until) {
                    return false;
                }
                if (stateBucket.compareAndSet(STATE_OPEN, STATE_HALF_OPEN)) {
                    untilBucket.delete();
                    redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE)).set(0);
                    redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE_OK)).set(0);
                    persist(key, STATE_HALF_OPEN, config, null, null);
                }
                return true;
            }
            if (STATE_HALF_OPEN.equals(state)) {
                RAtomicLong probe = redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE));
                long seq = probe.incrementAndGet();
                probe.expire(Duration.ofMinutes(5));
                return seq <= halfOpenProbes(config);
            }
            return true;
        } catch (Exception ex) {
            log.warn("[allowRequest][熔断器异常，降级放行 key={}] {}", key, ex.getMessage());
            return true;
        }
    }

    /**
     * 记录一次成功。HALF_OPEN 下探测成功数达标后闭合熔断器。
     */
    public void recordSuccess(AiModelConfig config) {
        if (!isEnabled(config)) {
            return;
        }
        String key = circuitKey(config);
        try {
            long total = countWindow(key, config, false);
            RBucket<String> stateBucket = stateBucket(key);
            if (STATE_HALF_OPEN.equals(stateBucket.get())) {
                RAtomicLong ok = redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE_OK));
                long succeeded = ok.incrementAndGet();
                ok.expire(Duration.ofMinutes(5));
                if (succeeded >= halfOpenProbes(config)
                        && stateBucket.compareAndSet(STATE_HALF_OPEN, STATE_CLOSED)) {
                    redissonClient.getBucket(redisKey(key, SUFFIX_OPEN_UNTIL)).delete();
                    redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE)).set(0);
                    ok.set(0);
                    persist(key, STATE_CLOSED, config, total, 0L);
                }
            }
        } catch (Exception ex) {
            log.warn("[recordSuccess][记录失败 key={}] {}", key, ex.getMessage());
        }
    }

    /**
     * 记录一次失败。
     *
     * 只有「下游故障」计入失败率：AUTH / BAD_REQUEST / CONTENT_FILTER 是调用方问题，
     * 既不该重试，也不该把熔断器打开（一次配置错误会让整个模型被误判为故障）。
     */
    public void recordFailure(AiModelConfig config, ModelErrorCategory category, String errorCode) {
        if (!isEnabled(config) || category == null || !category.downstreamFailure()) {
            return;
        }
        String key = circuitKey(config);
        try {
            long total = countWindow(key, config, true);
            RBucket<String> stateBucket = stateBucket(key);
            String state = stateBucket.get();
            if (STATE_HALF_OPEN.equals(state)) {
                // 半开期间任何一次下游失败立即退回 OPEN
                if (stateBucket.compareAndSet(STATE_HALF_OPEN, STATE_OPEN)) {
                    openUntil(key, config);
                    persist(key, STATE_OPEN, config, total, failedCount(key, config));
                }
                return;
            }
            if (!STATE_CLOSED.equals(state)) {
                return;
            }
            long failed = failedCount(key, config);
            int minSample = config.getCircuitMinSample() == null ? 20 : config.getCircuitMinSample();
            int threshold = config.getCircuitFailureRate() == null ? 50 : config.getCircuitFailureRate();
            if (total >= minSample && failed * 100 >= total * threshold
                    && stateBucket.compareAndSet(STATE_CLOSED, STATE_OPEN)) {
                openUntil(key, config);
                persist(key, STATE_OPEN, config, total, failed);
            }
        } catch (Exception ex) {
            log.warn("[recordFailure][记录失败 key={}] {}", key, ex.getMessage());
        }
    }

    /**
     * 轻量读取当前熔断状态（只读 Redis，供每次调用的日志埋点使用，避免逐条查 DB）
     */
    public String currentState(String circuitKey) {
        try {
            String state = stateBucket(circuitKey).get();
            return StringUtils.hasText(state) ? state : STATE_CLOSED;
        } catch (Exception ex) {
            return STATE_CLOSED;
        }
    }

    /**
     * 读取实时状态（Redis 覆盖 DB 快照），供管理端展示
     */
    public AiModelCircuitState snapshot(String circuitKey) {
        AiModelCircuitState entity = aiModelCircuitStateService.getAiModelCircuitStateByKey(circuitKey);
        if (entity == null) {
            entity = new AiModelCircuitState();
            entity.setCircuitKey(circuitKey);
            entity.setState(STATE_CLOSED);
            entity.setWindowTotal(0);
            entity.setWindowFailed(0);
            entity.setWindowSucceeded(0);
            entity.setFailureRate(BigDecimal.ZERO);
            entity.setConsecutiveFailures(0);
            entity.setConsecutiveSuccesses(0);
            entity.setHalfOpenProbes(0);
            entity.setHalfOpenSucceeded(0);
            entity.setVersion(0);
        }
        try {
            String state = stateBucket(circuitKey).get();
            if (StringUtils.hasText(state)) {
                entity.setState(state);
            }
            RBucket<Long> untilBucket = redissonClient.getBucket(redisKey(circuitKey, SUFFIX_OPEN_UNTIL));
            Long until = untilBucket.get();
            entity.setOpenUntil(until == null ? null : toLocalDateTime(until));
            entity.setHalfOpenProbes((int) redissonClient.getAtomicLong(
                    redisKey(circuitKey, SUFFIX_PROBE)).get());
            entity.setHalfOpenSucceeded((int) redissonClient.getAtomicLong(
                    redisKey(circuitKey, SUFFIX_PROBE_OK)).get());
        } catch (Exception ex) {
            log.warn("[snapshot][读取 Redis 状态失败 key={}] {}", circuitKey, ex.getMessage());
        }
        return entity;
    }

    /**
     * 人工止血：清 Redis 状态与限流 key，并把 DB 快照复位为 CLOSED
     */
    public void reset(String circuitKey) {
        try {
            for (String suffix : new String[]{SUFFIX_STATE, SUFFIX_OPEN_UNTIL, SUFFIX_PROBE, SUFFIX_PROBE_OK}) {
                redissonClient.getBucket(redisKey(circuitKey, suffix)).delete();
            }
            // 窗口计数 key 带时间后缀，靠自身 TTL 到期清理，不在此逐个删除
            modelRateLimiter.reset(circuitKey);
        } catch (Exception ex) {
            log.warn("[reset][清理 Redis 失败 key={}] {}", circuitKey, ex.getMessage());
        }
        AiModelCircuitState entity = aiModelCircuitStateService.getAiModelCircuitStateByKey(circuitKey);
        if (entity != null) {
            entity.setState(STATE_CLOSED);
            entity.setWindowTotal(0);
            entity.setWindowFailed(0);
            entity.setWindowSucceeded(0);
            entity.setFailureRate(BigDecimal.ZERO);
            entity.setConsecutiveFailures(0);
            entity.setConsecutiveSuccesses(0);
            entity.setHalfOpenProbes(0);
            entity.setHalfOpenSucceeded(0);
            entity.setOpenedTime(null);
            entity.setOpenUntil(null);
            entity.setUpdateTime(LocalDateTime.now());
            entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + 1);
            aiModelCircuitStateService.updateById(entity);
        }
    }

    // ========== 内部方法 ==========

    private RBucket<String> stateBucket(String key) {
        return redissonClient.getBucket(redisKey(key, SUFFIX_STATE));
    }

    private String redisKey(String key, String suffix) {
        return String.format(KEY_PREFIX, key) + suffix;
    }

    private int halfOpenProbes(AiModelConfig config) {
        return config.getCircuitHalfOpenProbes() == null || config.getCircuitHalfOpenProbes() <= 0
                ? 3 : config.getCircuitHalfOpenProbes();
    }

    private long windowStart(AiModelConfig config) {
        int seconds = config.getCircuitWindowSeconds() == null || config.getCircuitWindowSeconds() <= 0
                ? 60 : config.getCircuitWindowSeconds();
        long windowMillis = seconds * 1000L;
        return System.currentTimeMillis() / windowMillis * windowMillis;
    }

    /** 窗口计数，返回窗口内总请求数 */
    private long countWindow(String key, AiModelConfig config, boolean failed) {
        String prefix = String.format(KEY_PREFIX, key) + "win:" + windowStart(config) + ":";
        RAtomicLong total = redissonClient.getAtomicLong(prefix + "total");
        long totalValue = total.incrementAndGet();
        int seconds = config.getCircuitWindowSeconds() == null || config.getCircuitWindowSeconds() <= 0
                ? 60 : config.getCircuitWindowSeconds();
        Duration ttl = Duration.ofSeconds(seconds * 2L);
        total.expire(ttl);
        if (failed) {
            RAtomicLong failedCounter = redissonClient.getAtomicLong(prefix + "failed");
            failedCounter.incrementAndGet();
            failedCounter.expire(ttl);
        }
        return totalValue;
    }

    private long failedCount(String key, AiModelConfig config) {
        String prefix = String.format(KEY_PREFIX, key) + "win:" + windowStart(config) + ":";
        return redissonClient.getAtomicLong(prefix + "failed").get();
    }

    private void openUntil(String key, AiModelConfig config) {
        int seconds = config.getCircuitOpenSeconds() == null || config.getCircuitOpenSeconds() <= 0
                ? 30 : config.getCircuitOpenSeconds();
        RBucket<Long> bucket = redissonClient.getBucket(redisKey(key, SUFFIX_OPEN_UNTIL));
        bucket.set(System.currentTimeMillis() + seconds * 1000L);
        redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE)).set(0);
        redissonClient.getAtomicLong(redisKey(key, SUFFIX_PROBE_OK)).set(0);
    }

    /** 只在状态跃迁时写 DB 快照，避免每次调用产生热点行 */
    private void persist(String key, String state, AiModelConfig config, Long total, Long failed) {
        try {
            AiModelCircuitState entity = aiModelCircuitStateService.getAiModelCircuitStateByKey(key);
            boolean create = entity == null;
            if (create) {
                entity = new AiModelCircuitState();
                entity.setCircuitKey(key);
                entity.setModelConfigId(config.getId());
                entity.setCreator(currentUser());
                entity.setCreateTime(LocalDateTime.now());
                entity.setDeleted(0);
                entity.setVersion(0);
            }
            entity.setState(state);
            entity.setWindowStartTime(toLocalDateTime(windowStart(config)));
            if (total != null) {
                entity.setWindowTotal(total.intValue());
            }
            if (failed != null) {
                entity.setWindowFailed(failed.intValue());
                entity.setWindowSucceeded(Math.max(0, (total == null ? 0 : total.intValue()) - failed.intValue()));
                entity.setFailureRate(total == null || total == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(failed * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
            }
            if (STATE_OPEN.equals(state)) {
                entity.setOpenedTime(LocalDateTime.now());
                RBucket<Long> untilBucket = redissonClient.getBucket(redisKey(key, SUFFIX_OPEN_UNTIL));
                Long until = untilBucket.get();
                entity.setOpenUntil(until == null ? null : toLocalDateTime(until));
            } else {
                entity.setOpenedTime(null);
                entity.setOpenUntil(null);
            }
            entity.setUpdateTime(LocalDateTime.now());
            entity.setUpdater(currentUser());
            entity.setVersion((entity.getVersion() == null ? 0 : entity.getVersion()) + 1);
            if (create) {
                aiModelCircuitStateService.save(entity);
            } else {
                aiModelCircuitStateService.updateById(entity);
            }
        } catch (Exception ex) {
            log.warn("[persist][写熔断快照失败 key={}] {}", key, ex.getMessage());
        }
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

}
