package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模型调用日志（用量与成本）表
 * @TableName ai_model_call_log
 */
@TableName(value ="ai_model_call_log")
@Data
public class AiModelCallLog {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 全链路追踪 ID
     */
    @TableField(value = "trace_id")
    private String traceId;

    /**
     * 会话 ID
     */
    @TableField(value = "conversation_id")
    private Long conversationId;

    /**
     * 场景编码
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 
     */
    @TableField(value = "provider_id")
    private Long providerId;

    /**
     * 
     */
    @TableField(value = "model_config_id")
    private Long modelConfigId;

    /**
     * 模型标识快照，避免配置变更影响历史
     */
    @TableField(value = "model_id")
    private String modelId;

    /**
     * 
     */
    @TableField(value = "request_tokens")
    private Integer requestTokens;

    /**
     * 
     */
    @TableField(value = "response_tokens")
    private Integer responseTokens;

    /**
     * 
     */
    @TableField(value = "total_tokens")
    private Integer totalTokens;

    /**
     * 本次调用费用
     */
    @TableField(value = "cost")
    private BigDecimal cost;

    /**
     * 
     */
    @TableField(value = "latency_ms")
    private Integer latencyMs;

    /**
     * 0 成功、1 失败、2 超时、3 降级
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 降级前的模型 ID
     */
    @TableField(value = "fallback_from_model_id")
    private Long fallbackFromModelId;

    /**
     * 
     */
    @TableField(value = "error_code")
    private String errorCode;

    /**
     * 
     */
    @TableField(value = "error_msg")
    private String errorMsg;

    /**
     * 
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * 
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * 
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * 
     */
    @TableField(value = "deleted")
    private Integer deleted;

    /**
     * 是否命中灰度流量：0 否、1 是
     */
    @TableField(value = "is_gray")
    private Integer isGray;

    /**
     * 灰度分组：A 对照组、B 灰度组；未参与实验留空
     */
    @TableField(value = "gray_group")
    private String grayGroup;

    /**
     * 关联 ai_model_gray_experiment.id，0 表示非实验流量
     */
    @TableField(value = "gray_experiment_id")
    private Long grayExperimentId;

    /**
     * 本次实际使用的分桶维度快照，便于复盘归因
     */
    @TableField(value = "gray_bucket_key")
    private String grayBucketKey;

    /**
     * 重试 / 降级调用指向的原始 trace_id，首次调用留空；成本按 COALESCE(NULLIF(retry_of_trace_id,空串), trace_id) 聚合
     */
    @TableField(value = "retry_of_trace_id")
    private String retryOfTraceId;

    /**
     * 第几次重试，0 = 首次调用
     */
    @TableField(value = "retry_index")
    private Integer retryIndex;

    /**
     * 本次调用发生时的熔断状态：CLOSED / OPEN / HALF_OPEN；OPEN 且被快速失败时不产生供应商调用
     */
    @TableField(value = "circuit_state")
    private String circuitState;

    /**
     * 熔断维度键快照 provider_id:model_config_id
     */
    @TableField(value = "circuit_key")
    private String circuitKey;

    /**
     * 失败细分类：RATE_LIMIT / SERVER_ERROR / TIMEOUT / CONNECT / AUTH / BAD_REQUEST / CONTENT_FILTER / INVALID_OUTPUT / CIRCUIT_OPEN；决定是否重试、是否计入熔断
     */
    @TableField(value = "error_category")
    private String errorCategory;

    /**
     * 降级链第几跳，0 = 未降级
     */
    @TableField(value = "fallback_depth")
    private Integer fallbackDepth;

    /**
     * 本次实际路由策略快照：1 固定、2 优先级、3 权重、4 成本优先
     */
    @TableField(value = "route_strategy")
    private Integer routeStrategy;

    /**
     * 进入模型客户端前的排队等待时长（毫秒），用于验证限流是否生效
     */
    @TableField(value = "queue_wait_ms")
    private Integer queueWaitMs;

    /**
     * 供应商侧请求 ID，用于对账与排查「首次已成功但响应丢失」导致的重复扣费
     */
    @TableField(value = "request_id")
    private String requestId;
}