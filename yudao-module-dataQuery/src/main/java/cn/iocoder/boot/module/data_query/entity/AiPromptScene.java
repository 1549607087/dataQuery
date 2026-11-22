package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

import cn.iocoder.boot.module.data_query.framework.tenant.SharedEntity;

/**
 * 提示词场景字典表
 * @TableName ai_prompt_scene
 */
@TableName(value ="ai_prompt_scene")
@Data
public class AiPromptScene implements SharedEntity {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 场景编码，与 ai_model_scene_binding.scene_code 一致：INTENT / REWRITE / SCHEMA_RECALL / SQL_GEN / SQL_FIX / EXPLAIN / RESULT_SUMMARY / REPORT / CHART / RECOMMEND / RAG / PREDICT
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 场景名称
     */
    @TableField(value = "scene_name")
    private String sceneName;

    /**
     * 场景用途说明
     */
    @TableField(value = "description")
    private String description;

    /**
     * 该场景默认占位符集合，JSON 数组，如 ["question","schema"]
     */
    @TableField(value = "default_variables")
    private String defaultVariables;

    /**
     * 建议模型类型：1 Chat、4 SQL 专用，对应 ai_model_config.model_type
     */
    @TableField(value = "default_model_type")
    private Integer defaultModelType;

    /**
     * 状态：0 启用、1 停用
     */
    @TableField(value = "status")
    private Integer status;

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
     * 租户编号；场景字典为平台级，恒为 0，租户不复制
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
}