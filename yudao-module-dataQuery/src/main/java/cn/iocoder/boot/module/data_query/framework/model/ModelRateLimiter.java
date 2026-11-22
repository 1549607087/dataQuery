package cn.iocoder.boot.module.data_query.framework.model;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RSemaphore;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * 模型级限流器（QPS + 并发）
 *
 * 为什么不用框架 starter-protection 的 @RateLimiter：那是接口级限流，按全局 / 用户 / IP 维度，
 * 管不了「某个供应商账号每分钟 60 次」这类配额。模型配额必须按 provider_id + model_config_id
 * （即 circuitKey）维度做，且要跨实例共享，所以落在 Redis。
 *
 * QPS 与并发约束的是两个不同瓶颈：QPS 对应供应商 RPM/TPM 配额（429 的直接原因），
 * 并发对应本机线程池 / 连接池的在途请求数。按 Little's Law 并发 ≈ 到达率 × 平均响应时间，
 * 超时设得越长，需要的并发槽越多，两者必须一起调。
 */
@Slf4j
@Component
public class ModelRateLimiter {

    private static final String QPS_KEY = "ai:model:qps:%s";
    private static final String CONC_KEY = "ai:model:conc:%s";

    /** 限流器自身过期时间，避免长期不用的模型在 Redis 里堆积 key */
    private static final Duration KEY_TTL = Duration.ofMinutes(10);

    @Resource
    private RedissonClient redissonClient;

    /**
     * 尝试获取 1 个 QPS 令牌
     *
     * @return false 表示本模型已达速率上限，调用方应直接换候选模型（不要重试，重试只会放大流量）
     */
    public boolean tryAcquireQps(String circuitKey, int qpsLimit) {
        if (qpsLimit <= 0) {
            return true; // 0 表示不限
        }
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(String.format(QPS_KEY, circuitKey));
            RateLimiterConfig config = limiter.getConfig();
            Duration interval = Duration.ofSeconds(1);
            long rateIntervalMillis = 1000L;
            if (config == null) {
                limiter.trySetRate(RateType.OVERALL, qpsLimit, interval);
                limiter.expire(KEY_TTL);
            } else if (config.getRateType() != RateType.OVERALL
                    || !Objects.equals(config.getRate(), (long) qpsLimit)
                    || !Objects.equals(config.getRateInterval(), rateIntervalMillis)) {
                // 管理端改了 qps_limit，重新设置速率（照框架 RateLimiterRedisDAO 的处理方式）
                limiter.setRate(RateType.OVERALL, qpsLimit, interval);
                limiter.expire(KEY_TTL);
            }
            return limiter.tryAcquire();
        } catch (Exception ex) {
            // Redis 故障时不能因为限流器把整个问答端打死，选择放行并记录
            log.warn("[tryAcquireQps][限流器异常，降级放行 key={}] {}", circuitKey, ex.getMessage());
            return true;
        }
    }

    /**
     * 尝试占用 1 个并发槽
     *
     * @param timeoutMs 排队等待上限，取本次调用的超时时间
     * @return false 表示排队超时或被打断
     */
    public boolean tryAcquireConcurrency(String circuitKey, int concurrentLimit, long timeoutMs) {
        if (concurrentLimit <= 0) {
            return true;
        }
        try {
            RSemaphore semaphore = redissonClient.getSemaphore(String.format(CONC_KEY, circuitKey));
            if (!semaphore.isExists()) {
                // trySetPermits 只在 key 不存在时生效；管理端改了并发上限后需调 reset(circuitKey) 删 key
                semaphore.trySetPermits(concurrentLimit);
            }
            return semaphore.tryAcquire(Duration.ofMillis(Math.max(1, timeoutMs)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            log.warn("[tryAcquireConcurrency][信号量异常，降级放行 key={}] {}", circuitKey, ex.getMessage());
            return true;
        }
    }

    /**
     * 释放并发槽。必须与一次成功的 tryAcquireConcurrency 严格配对。
     */
    public void releaseConcurrency(String circuitKey, int concurrentLimit) {
        if (concurrentLimit <= 0) {
            return;
        }
        try {
            redissonClient.getSemaphore(String.format(CONC_KEY, circuitKey)).release();
        } catch (Exception ex) {
            log.warn("[releaseConcurrency][释放失败 key={}] {}", circuitKey, ex.getMessage());
        }
    }

    /**
     * 清除该模型的限流 key。管理端调整 QPS / 并发上限后调用，否则并发信号量的旧 permits 不会更新。
     */
    public void reset(String circuitKey) {
        try {
            redissonClient.getRateLimiter(String.format(QPS_KEY, circuitKey)).delete();
            redissonClient.getSemaphore(String.format(CONC_KEY, circuitKey)).delete();
        } catch (Exception ex) {
            log.warn("[reset][清理限流 key 失败 key={}] {}", circuitKey, ex.getMessage());
        }
    }

}
