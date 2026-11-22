package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模型路由候选集（权重 / 优先级 / 成本优先）表
 * @TableName ai_model_route_candidate
 */
@TableName(value ="ai_model_route_candidate")
@Data
public class AiModelRouteCandidate {
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
     * 关联 ai_model_scene_binding.id
     */
    @TableField(value = "binding_id")
    private Long bindingId;

    /**
     * 候选模型，关联 ai_model_config.id
     */
    @TableField(value = "model_id")
    private Long modelId;

    /**
     * 供应商 ID 快照，关联 ai_model_provider.id，用于跨供应商降级校验
     */
    @TableField(value = "provider_id")
    private Long providerId;

    /**
     * 权重（策略 3 用），相对值，运行时按候选集归一化
     */
    @TableField(value = "weight")
    private Integer weight;

    /**
     * 优先级（策略 2 用），数值越小越优先；也作为降级链顺序
     */
    @TableField(value = "priority")
    private Integer priority;

    /**
     * 该候选单独可承载的 QPS 上限，0 表示不限
     */
    @TableField(value = "max_qps")
    private Integer maxQps;

    /**
     * 是否参与路由：0 否、1 是
     */
    @TableField(value = "enabled")
    private Integer enabled;

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