package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

import cn.iocoder.boot.module.data_query.framework.tenant.SharedEntity;

/**
 * 提示词模板主表（当前生效内容）
 * @TableName ai_prompt
 */
@TableName(value ="ai_prompt")
@Data
public class AiPrompt implements SharedEntity {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 场景编码，关联 ai_prompt_scene.scene_code；同一场景可有多条模板（默认/灰度/A-B）
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 提示词名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 提示词编码，租户内唯一，供程序按编码引用
     */
    @TableField(value = "code")
    private String code;

    /**
     * 类型：1 System、2 User、3 完整模板（含多段消息）
     */
    @TableField(value = "prompt_type")
    private Integer promptType;

    /**
     * 模板正文，占位符统一使用 {{var_name}}
     */
    @TableField(value = "content")
    private String content;

    /**
     * 变量定义只读快照 JSON，由 ai_prompt_variable 发布时生成；运行时按此渲染
     */
    @TableField(value = "variable_defs")
    private String variableDefs;

    /**
     * 默认模型，关联 ai_model_config.id；0 表示跟随 ai_model_scene_binding 的路由结果
     */
    @TableField(value = "model_id")
    private Long modelId;

    /**
     * 模型参数覆盖 JSON：temperature / top_p / max_tokens
     */
    @TableField(value = "params")
    private String params;

    /**
     * 来源：1 平台内置、2 租户自定义、3 复制自平台模板
     */
    @TableField(value = "source")
    private Integer source;

    /**
     * 继承/复制来源 ai_prompt.id，0 表示原始模板
     */
    @TableField(value = "parent_id")
    private Long parentId;

    /**
     * 当前版本号，与 ai_prompt_version.version 对应
     */
    @TableField(value = "version")
    private Integer version;

    /**
     * 当前生效版本，关联 ai_prompt_version.id
     */
    @TableField(value = "current_version_id")
    private Long currentVersionId;

    /**
     * 状态：0 启用、1 停用、2 灰度
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 灰度流量比例 0-100，仅 status=2 时生效
     */
    @TableField(value = "gray_ratio")
    private Integer grayRatio;

    /**
     * 同场景内的优先级，数值越小越优先
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

    /**
     * 租户编号；0 为平台级默认，租户可复制同 code 覆盖
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