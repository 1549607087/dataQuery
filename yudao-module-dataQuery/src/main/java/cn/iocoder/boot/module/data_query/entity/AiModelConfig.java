package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模型配置表
 * @TableName ai_model_config
 */
@TableName(value ="ai_model_config")
@Data
public class AiModelConfig {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_model_provider.id
     */
    @TableField(value = "provider_id")
    private Long providerId;

    /**
     * 模型显示名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 调用时使用的模型标识，如 qwen-plus / gpt-4o / bge-m3
     */
    @TableField(value = "model_id")
    private String modelId;

    /**
     * 类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用
     */
    @TableField(value = "model_type")
    private Integer modelType;

    /**
     * 能力标签 JSON：streaming / function_call / json_mode / vision
     */
    @TableField(value = "capability")
    private String capability;

    /**
     * 上下文窗口（token）
     */
    @TableField(value = "context_window")
    private Integer contextWindow;

    /**
     * 最大输出 token
     */
    @TableField(value = "max_output_tokens")
    private Integer maxOutputTokens;

    /**
     * 默认参数 JSON：temperature / top_p / presence_penalty 等
     */
    @TableField(value = "default_params")
    private String defaultParams;

    /**
     * 是否支持流式：0 否、1 是
     */
    @TableField(value = "support_stream")
    private Integer supportStream;

    /**
     * 输入单价（元 / 百万 token）
     */
    @TableField(value = "input_price")
    private BigDecimal inputPrice;

    /**
     * 输出单价（元 / 百万 token）
     */
    @TableField(value = "output_price")
    private BigDecimal outputPrice;

    /**
     * 币种
     */
    @TableField(value = "currency")
    private String currency;

    /**
     * QPS 上限，0 表示不限
     */
    @TableField(value = "qps_limit")
    private Integer qpsLimit;

    /**
     * 并发上限，0 表示不限
     */
    @TableField(value = "concurrent_limit")
    private Integer concurrentLimit;

    /**
     * 单次调用超时（毫秒）
     */
    @TableField(value = "timeout_ms")
    private Integer timeoutMs;

    /**
     * 失败重试次数
     */
    @TableField(value = "retry_count")
    private Integer retryCount;

    /**
     * 状态：0 启用、1 停用、2 灰度
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 灰度流量比例 0-100
     */
    @TableField(value = "gray_ratio")
    private Integer grayRatio;

    /**
     * 健康状态：0 未检测、1 健康、2 异常
     */
    @TableField(value = "health_status")
    private Integer healthStatus;

    /**
     * 最近一次连通性检测时间
     */
    @TableField(value = "last_check_time")
    private LocalDateTime lastCheckTime;

    /**
     * 最近一次检测结果
     */
    @TableField(value = "last_check_result")
    private String lastCheckResult;

    /**
     * 排序
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

    /**
     * 租户编号
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
     * 是否启用熔断：0 否、1 是；默认关闭，避免升级后误伤
     */
    @TableField(value = "circuit_enabled")
    private Integer circuitEnabled;

    /**
     * 熔断失败率阈值（百分比），窗口内失败率超过该值且样本数足够才打开
     */
    @TableField(value = "circuit_failure_rate")
    private Integer circuitFailureRate;

    /**
     * 窗口内最小请求数，不足则不熔断，防止小样本误判
     */
    @TableField(value = "circuit_min_sample")
    private Integer circuitMinSample;

    /**
     * 熔断统计窗口长度（秒），建议 30~120
     */
    @TableField(value = "circuit_window_seconds")
    private Integer circuitWindowSeconds;

    /**
     * OPEN 状态持续时长（秒），到期自动进入 HALF_OPEN
     */
    @TableField(value = "circuit_open_seconds")
    private Integer circuitOpenSeconds;

    /**
     * HALF_OPEN 允许的探测请求数，成功达标才回到 CLOSED
     */
    @TableField(value = "circuit_half_open_probes")
    private Integer circuitHalfOpenProbes;

    /**
     * 可重试错误分类白名单，逗号分隔；401 / 400 / CONTENT_FILTER / INVALID_OUTPUT 绝不重试
     */
    @TableField(value = "retryable_errors")
    private String retryableErrors;

    /**
     * 指数退避基数（毫秒），200 -> 400 -> 800，配合抖动避免同步风暴
     */
    @TableField(value = "retry_base_backoff_ms")
    private Integer retryBaseBackoffMs;

    /**
     * 单次退避上限（毫秒）
     */
    @TableField(value = "retry_max_backoff_ms")
    private Integer retryMaxBackoffMs;

    /**
     * 退避抖动系数 0.00-1.00，建议 0.30
     */
    @TableField(value = "retry_jitter_ratio")
    private BigDecimal retryJitterRatio;

    /**
     * 熔断维度键，默认 provider_id:model_config_id；多实例共享 Redis 状态用同一 key
     */
    @TableField(value = "circuit_key")
    private String circuitKey;
}