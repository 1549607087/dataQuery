package cn.iocoder.boot.module.data_query.framework.chat;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在途问答请求的取消注册表。
 *
 * <p>key = tenantId + "|" + clientRequestId，保证跨租户隔离：A 租户无法取消 B 租户的请求。
 * 前端发起 ask-stream 时用 clientRequestId 注册，点停止时凭同一个 id 定位令牌并触发取消；
 * ask 结束（含正常/异常/取消）时移除，避免泄漏。注册时顺带做惰性 TTL 清理兜底。
 */
@Component
public class ChatCancelRegistry {

    @Resource
    private ChatCancelProperties properties;

    private final ConcurrentHashMap<String, ChatCancelToken> inFlight = new ConcurrentHashMap<>();

    /** 注册在途令牌；同 key 已存在时（前端重试同 id）先取消旧令牌再覆盖。 */
    public ChatCancelToken register(Long tenantId, String clientRequestId) {
        String key = buildKey(tenantId, clientRequestId);
        evictExpired();
        ChatCancelToken previous = inFlight.put(key, new ChatCancelToken(tenantId, clientRequestId));
        if (previous != null && !previous.isCancelled()) {
            previous.cancel();
        }
        return inFlight.get(key);
    }

    /**
     * 连接级令牌：**始终**返回非 null。
     *
     * <p>「客户端断连即取消」不能依赖前端是否传 clientRequestId、也不能受 /cancel 开关影响，
     * 否则关页面后模型会跑完整段、token 照常计费。因此这里无条件创建令牌，
     * 只有同时满足「开关开启 + 有 clientRequestId」才登记进注册表（即可被 /cancel 定位）。
     *
     * @param traceId 用于给不可取消的连接生成可追踪的内部 id（写入日志）
     */
    public ChatCancelToken registerForConnection(Long tenantId, String clientRequestId, String traceId) {
        if (isEnabled() && clientRequestId != null && !clientRequestId.isBlank()) {
            return register(tenantId, clientRequestId);
        }
        String fallback = "conn-" + (traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId);
        return new ChatCancelToken(tenantId, fallback);
    }

    /** 定位并触发取消；返回是否命中在途请求。开关关闭时恒返回 false。 */
    public boolean cancel(Long tenantId, String clientRequestId) {
        if (!isEnabled()) {
            return false;
        }
        ChatCancelToken token = inFlight.get(buildKey(tenantId, clientRequestId));
        if (token == null) {
            return false;
        }
        token.cancel(ChatCancelToken.Reason.USER);
        return true;
    }

    /** 仅取出令牌（不触发取消），供需要直接操作令牌的场景使用。 */
    public ChatCancelToken get(Long tenantId, String clientRequestId) {
        return inFlight.get(buildKey(tenantId, clientRequestId));
    }

    /** 移除在途令牌，必须在 ask 的 finally 中调用。 */
    public void remove(Long tenantId, String clientRequestId) {
        inFlight.remove(buildKey(tenantId, clientRequestId));
    }

    public boolean isEnabled() {
        return properties != null && Boolean.TRUE.equals(properties.getEnabled());
    }

    private String buildKey(Long tenantId, String clientRequestId) {
        return (tenantId == null ? "null" : tenantId) + "|" + clientRequestId;
    }

    /** 惰性清理超时令牌；仅在 register 时触发，成本摊到每次新请求。 */
    private void evictExpired() {
        int ttl = properties == null || properties.getTokenTtlMs() == null
                ? 300000 : properties.getTokenTtlMs();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ChatCancelToken> entry : inFlight.entrySet()) {
            if (now - entry.getValue().getCreatedAt() > ttl) {
                inFlight.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 供单测/监控读取当前在途数量。 */
    int size() {
        return inFlight.size();
    }
}
