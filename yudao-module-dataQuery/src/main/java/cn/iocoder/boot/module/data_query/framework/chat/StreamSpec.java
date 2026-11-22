package cn.iocoder.boot.module.data_query.framework.chat;

/**
 * 一条 SSE 连接的横切属性（与业务动作无关的部分）。
 *
 * <p>业务差异通过 {@code stream(spec, action)} 的 action 注入，不放进本对象——
 * 否则以后加一个入口就要往这里加 if，装配层会重新变成三份。
 *
 * <p>{@code op} + {@code sourceMessageId} 只用于重跑在途去重：ASK 为 null，不拦连点两次发送。
 *
 * @param knowledge       是否走知识库的开关 / 超时配置
 * @param clientRequestId 客户端请求编号，可空（缺失时服务端按 traceId 生成连接级令牌）
 * @param op              ASK / REGENERATE / EDIT
 * @param sourceMessageId 重跑源消息 id
 */
public record StreamSpec(boolean knowledge, String clientRequestId, String op, Long sourceMessageId) {

    public static StreamSpec ask(boolean knowledge, String clientRequestId) {
        return new StreamSpec(knowledge, clientRequestId, null, null);
    }

    public static StreamSpec regenerate(boolean knowledge, String clientRequestId, Long sourceMessageId) {
        return new StreamSpec(knowledge, clientRequestId, "REGENERATE", sourceMessageId);
    }

    public static StreamSpec edit(boolean knowledge, String clientRequestId, Long sourceMessageId) {
        return new StreamSpec(knowledge, clientRequestId, "EDIT", sourceMessageId);
    }
}
