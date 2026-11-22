package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模型场景绑定（路由与降级）表
 * @TableName ai_model_scene_binding
 */
@TableName(value ="ai_model_scene_binding")
@Data
public class AiModelSceneBinding {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 场景编码：INTENT / REWRITE / SCHEMA_RECALL / SQL_GEN / SQL_FIX / EXPLAIN / RESULT_SUMMARY / REPORT / CHART / RECOMMEND / RAG / EMBEDDING / RERANK / PREDICT
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 场景名称
     */
    @TableField(value = "scene_name")
    private String sceneName;

    /**
     * 主模型，关联 ai_model_config.id
     */
    @TableField(value = "primary_model_id")
    private Long primaryModelId;

    /**
     * 降级链，模型 ID 逗号分隔，按顺序尝试
     */
    @TableField(value = "fallback_model_ids")
    private String fallbackModelIds;

    /**
     * 路由策略：1 固定、2 优先级、3 权重、4 成本优先
     */
    @TableField(value = "route_strategy")
    private Integer routeStrategy;

    /**
     * 关联提示词模板（逻辑关联，M2 模块）
     */
    @TableField(value = "prompt_id")
    private Long promptId;

    /**
     * 场景级温度覆盖，为空则用模型默认值
     */
    @TableField(value = "temperature_override")
    private BigDecimal temperatureOverride;

    /**
     * 是否启用：0 否、1 是
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
     * 场景级超时覆盖（毫秒），NULL 用模型默认；交互式 SQL_GEN 建议 4000~8000，离线 REPORT 可 60000
     */
    @TableField(value = "timeout_ms_override")
    private Integer timeoutMsOverride;

    /**
     * 场景级 QPS 覆盖，NULL 用模型默认，0 表示不限
     */
    @TableField(value = "qps_limit_override")
    private Integer qpsLimitOverride;

    /**
     * 场景级并发上限覆盖，NULL 用模型默认，0 表示不限
     */
    @TableField(value = "concurrent_limit_override")
    private Integer concurrentLimitOverride;

    /**
     * 场景级重试次数覆盖，NULL 用模型默认
     */
    @TableField(value = "retry_count_override")
    private Integer retryCountOverride;

    /**
     * 降级链是否强制跨供应商：0 否、1 是；同供应商双模型等于没降级
     */
    @TableField(value = "fallback_cross_provider")
    private Integer fallbackCrossProvider;

    /**
     * 单次请求最多降级跳数，防止降级链过长把延迟和成本放大
     */
    @TableField(value = "fallback_max_depth")
    private Integer fallbackMaxDepth;

    /**
     * 场景级灰度比例 0-100，优先于 ai_model_config.gray_ratio；一次只灰度一个变量
     */
    @TableField(value = "gray_ratio")
    private Integer grayRatio;

    /**
     * 灰度稳定分桶维度：TENANT / USER / SESSION；必须稳定哈希，否则同一用户两次命中不同模型，A/B 无法归因
     */
    @TableField(value = "gray_bucket_key")
    private String grayBucketKey;

    /**
     * 当前生效的灰度实验，关联 ai_model_gray_experiment.id，0 表示无实验
     */
    @TableField(value = "gray_experiment_id")
    private Long grayExperimentId;
}