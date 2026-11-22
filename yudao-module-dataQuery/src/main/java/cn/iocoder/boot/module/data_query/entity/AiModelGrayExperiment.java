package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模型 / 提示词灰度实验表
 * @TableName ai_model_gray_experiment
 */
@TableName(value ="ai_model_gray_experiment")
@Data
public class AiModelGrayExperiment {
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
     * 实验名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 灰度变量：MODEL / PROMPT；一次实验只允许一个变量，避免归因污染
     */
    @TableField(value = "gray_object")
    private String grayObject;

    /**
     * 实验生效场景，关联 ai_model_scene_binding.scene_code
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 关联 ai_model_scene_binding.id
     */
    @TableField(value = "binding_id")
    private Long bindingId;

    /**
     * 对照组 A 模型 ID，关联 ai_model_config.id
     */
    @TableField(value = "baseline_model_id")
    private Long baselineModelId;

    /**
     * 灰度组 B 模型 ID，关联 ai_model_config.id
     */
    @TableField(value = "gray_model_id")
    private Long grayModelId;

    /**
     * 对照组 A 提示词 ID（gray_object=PROMPT 时用）
     */
    @TableField(value = "baseline_prompt_id")
    private Long baselinePromptId;

    /**
     * 灰度组 B 提示词 ID（gray_object=PROMPT 时用）
     */
    @TableField(value = "gray_prompt_id")
    private Long grayPromptId;

    /**
     * 灰度组流量比例 0-100，建议从 5 起步
     */
    @TableField(value = "gray_ratio")
    private Integer grayRatio;

    /**
     * 稳定分桶维度：TENANT / USER / SESSION
     */
    @TableField(value = "bucket_key")
    private String bucketKey;

    /**
     * 实验开始时间
     */
    @TableField(value = "start_time")
    private LocalDateTime startTime;

    /**
     * 实验结束时间
     */
    @TableField(value = "end_time")
    private LocalDateTime endTime;

    /**
     * 每组最小样本量，未达标不允许下结论；5% × 1000 问答/天 = 50 样本，判断不了 1% 级准确率差异
     */
    @TableField(value = "min_sample_size")
    private Integer minSampleSize;

    /**
     * 观察指标 JSON：sql_executable_rate / result_accuracy / p95_latency_ms / cost_per_query / fallback_rate
     */
    @TableField(value = "metrics")
    private String metrics;

    /**
     * 状态：0 草稿、1 进行中、2 已结束、3 已全量、4 已回滚
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 实验结论
     */
    @TableField(value = "conclusion")
    private String conclusion;

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