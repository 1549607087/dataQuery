package cn.iocoder.boot.module.data_query.framework.model;

import lombok.Data;

/**
 * 模型调用结果
 */
@Data
public class ModelInvocationResult {

    /** 模型输出正文 */
    private String content;

    /** 请求 token 数 */
    private Integer requestTokens;

    /** 响应 token 数 */
    private Integer responseTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 供应商侧请求 ID，用于排查「首次已成功但响应丢失」导致的重复扣费 */
    private String requestId;

    /** 实际使用的模型标识快照 */
    private String modelId;

    /** 实际使用的模型配置编号 */
    private Long modelConfigId;

    /** 是否由联调用的 EchoModelClient 产生（true 时禁止作为业务结果使用） */
    private Boolean echo;

    /** 最终采用的路由策略 */
    private Integer routeStrategy;

    /** 降级深度，0 表示主模型直出 */
    private Integer fallbackDepth;

    /** 是否命中灰度流量 */
    private Integer isGray;

    /** 灰度分组 A / B */
    private String grayGroup;

    /** 全链路追踪 ID */
    private String traceId;

    /** 端到端耗时（毫秒） */
    private Integer latencyMs;

}
