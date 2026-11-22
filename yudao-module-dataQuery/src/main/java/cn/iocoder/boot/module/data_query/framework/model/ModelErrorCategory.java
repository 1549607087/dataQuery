package cn.iocoder.boot.module.data_query.framework.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 模型调用失败分类
 *
 * 这个枚举是重试、降级、熔断三方决策的唯一依据：
 * - 是否可重试：由 ai_model_config.retryable_errors 白名单决定，默认见 DEFAULT_RETRYABLE。
 * - 是否计入熔断失败率：只有「可归责于下游」的故障才计入，见 downstreamFailure()。
 *   401 密钥错、400 参数错、内容审核拒绝是调用方问题，重试多少次都不会成功，
 *   也不该把熔断器打开（否则一次配置错误会让整个模型被误判为故障）。
 */
public enum ModelErrorCategory {

    RATE_LIMIT,       // 429 / 供应商配额耗尽
    SERVER_ERROR,     // 5xx
    TIMEOUT,          // 读超时 / 治理层 Future 超时
    CONNECT,          // 连接失败 / DNS / TLS
    INVALID_OUTPUT,   // 返回内容不合格式（SQL 生成场景的非法 JSON 等）
    AUTH,             // 401 / 403，密钥或权限错误
    BAD_REQUEST,      // 400，参数错误
    CONTENT_FILTER,   // 内容审核拒绝
    CIRCUIT_OPEN,     // 被熔断器快速失败，未产生供应商调用
    QUEUE_FULL,       // 治理层线程池队列满
    LOCAL_RATE_LIMIT, // 被本地 QPS / 并发限流拦截
    CONFIG_ERROR,     // 无可用模型 / 场景未配置
    ALL_FAILED;       // 重试与降级链全部耗尽

    /** 默认可重试分类，与 nl2sql-model-governance.sql 的 retryable_errors 默认值一致 */
    public static final Set<ModelErrorCategory> DEFAULT_RETRYABLE = EnumSet.of(
            RATE_LIMIT, SERVER_ERROR, TIMEOUT, CONNECT);

    /**
     * 是否属于「下游故障」，只有这类失败才计入熔断失败率
     */
    public boolean downstreamFailure() {
        return this == RATE_LIMIT || this == SERVER_ERROR
                || this == TIMEOUT || this == CONNECT || this == INVALID_OUTPUT;
    }

    /**
     * 判断配置里的白名单是否允许重试该分类
     *
     * @param retryableErrors 逗号分隔的分类名；为空时走默认集合
     */
    public boolean retryable(String retryableErrors) {
        if (retryableErrors == null || retryableErrors.isBlank()) {
            return DEFAULT_RETRYABLE.contains(this);
        }
        for (String item : retryableErrors.split(",")) {
            if (name().equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从异常推断分类。生产环境应由 ModelClient 直接抛出带 category 的 ModelException，
     * 这里只作为兜底（例如 HTTP 客户端抛的是原生异常）。
     */
    public static ModelErrorCategory from(Throwable ex) {
        if (ex instanceof ModelException me) {
            return me.getCategory();
        }
        if (ex instanceof java.util.concurrent.TimeoutException) {
            return TIMEOUT;
        }
        String msg = String.valueOf(ex.getMessage()).toLowerCase();
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("quota")) {
            return RATE_LIMIT;
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")) {
            return AUTH;
        }
        if (msg.contains("400") || msg.contains("bad request")) {
            return BAD_REQUEST;
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return TIMEOUT;
        }
        if (msg.contains("connect") || msg.contains("connection refused") || msg.contains("unknownhost")) {
            return CONNECT;
        }
        if (msg.contains("5") && (msg.contains("500") || msg.contains("502")
                || msg.contains("503") || msg.contains("504"))) {
            return SERVER_ERROR;
        }
        return SERVER_ERROR;
    }

}
