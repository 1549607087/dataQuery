package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模型熔断状态持久化快照表（运行时以 Redis 为准，本表不承载高频写入）
 * @TableName ai_model_circuit_state
 */
@TableName(value ="ai_model_circuit_state")
@Data
public class AiModelCircuitState {
    /**
     * 主键（雪花 ID）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 租户编号
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * 关联 ai_model_config.id
     */
    @TableField(value = "model_config_id")
    private Long modelConfigId;

    /**
     * 熔断维度键 provider_id:model_config_id，与 Redis key 一致
     */
    @TableField(value = "circuit_key")
    private String circuitKey;

    /**
     * 熔断状态：CLOSED 闭合、OPEN 打开（快速失败）、HALF_OPEN 半开（探测）
     */
    @TableField(value = "state")
    private String state;

    /**
     * 当前统计窗口起始时间
     */
    @TableField(value = "window_start_time")
    private LocalDateTime windowStartTime;

    /**
     * 窗口内总请求数（含成功与失败）
     */
    @TableField(value = "window_total")
    private Integer windowTotal;

    /**
     * 窗口内失败数（仅计入可归责于下游的失败）
     */
    @TableField(value = "window_failed")
    private Integer windowFailed;

    /**
     * 窗口内成功数
     */
    @TableField(value = "window_succeeded")
    private Integer windowSucceeded;

    /**
     * 窗口失败率（百分比）
     */
    @TableField(value = "failure_rate")
    private BigDecimal failureRate;

    /**
     * 连续失败次数
     */
    @TableField(value = "consecutive_failures")
    private Integer consecutiveFailures;

    /**
     * 连续成功次数（HALF_OPEN 判定用）
     */
    @TableField(value = "consecutive_successes")
    private Integer consecutiveSuccesses;

    /**
     * 最近一次失败时间
     */
    @TableField(value = "last_failure_time")
    private LocalDateTime lastFailureTime;

    /**
     * 最近一次失败分类
     */
    @TableField(value = "last_failure_category")
    private String lastFailureCategory;

    /**
     * 进入 OPEN 的时间
     */
    @TableField(value = "opened_time")
    private LocalDateTime openedTime;

    /**
     * OPEN 到期时间，到期转 HALF_OPEN
     */
    @TableField(value = "open_until")
    private LocalDateTime openUntil;

    /**
     * HALF_OPEN 已发出的探测请求数
     */
    @TableField(value = "half_open_probes")
    private Integer halfOpenProbes;

    /**
     * HALF_OPEN 探测成功数
     */
    @TableField(value = "half_open_succeeded")
    private Integer halfOpenSucceeded;

    /**
     * 乐观锁版本号，防止多实例并发写覆盖
     */
    @TableField(value = "version")
    private Integer version;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

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
}