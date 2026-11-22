package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_REGENERATE_INFLIGHT;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 重跑在途去重：同一道题同一时刻只允许一个重新生成 / 修改问题。
 *
 * <p>为什么用 Redis：单机内存锁在多实例部署下形同虚设，用户连点后被路由到另一台机器照样生成两份。
 *
 * <p>Redis 不可用时一律放行。这是本项目第一次在业务链路强依赖 Redis：
 * 缓存故障不能变成「重跑按钮点了没反应」，宁可偶尔多生成一版，也不能把功能整体卡死。
 * 这条降级分支必须有单测覆盖。
 */
@Slf4j
@Component
public class ChatInflightGuard {

    private static final String KEY_PREFIX = "dq:chat:inflight:";

    /** CAS 删除：只删自己持有的那把锁，避免旧请求 finally 把新请求的锁删掉 */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private KnowledgeProperties properties;

    /**
     * @return 本次持有的锁令牌；未加锁（ASK / 参数缺失 / Redis 故障）时返回 null
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 同一轮已在途时抛 124
     */
    public String acquire(Long tenantId, Long userId, String op, Long sourceMessageId) {
        if (op == null || sourceMessageId == null || tenantId == null || userId == null) {
            return null;
        }
        String key = buildKey(tenantId, userId, op, sourceMessageId);
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            long ttlMs = properties.getSseTimeoutMs() == null ? 310_000L : properties.getSseTimeoutMs() + 10_000L;
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, Duration.ofMillis(ttlMs));
            if (!Boolean.TRUE.equals(ok)) {
                throw exception(CHAT_REGENERATE_INFLIGHT);
            }
            return token;
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
            // 明确的业务拒绝，不能被下面的 Redis 降级吞掉
            throw ex;
        } catch (Exception ex) {
            log.warn("[chat][inflight] Redis 不可用，放行本次重跑 key={} reason={}", key, ex.toString());
            return null;
        }
    }

    /** 释放锁；token 为 null（未加锁）时直接返回。释放失败由 TTL 兜底。 */
    public void release(Long tenantId, Long userId, String op, Long sourceMessageId, String token) {
        if (token == null || op == null || sourceMessageId == null || tenantId == null || userId == null) {
            return;
        }
        String key = buildKey(tenantId, userId, op, sourceMessageId);
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        } catch (Exception ex) {
            log.warn("[chat][inflight] 释放锁失败，等待 TTL 过期 key={} reason={}", key, ex.toString());
        }
    }

    private static String buildKey(Long tenantId, Long userId, String op, Long sourceMessageId) {
        return KEY_PREFIX + tenantId + ":" + userId + ":" + op + ":" + sourceMessageId;
    }
}
