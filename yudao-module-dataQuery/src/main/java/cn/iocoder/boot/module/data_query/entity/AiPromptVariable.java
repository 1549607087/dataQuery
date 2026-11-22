package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

import cn.iocoder.boot.module.data_query.framework.tenant.SharedEntity;

/**
 * 提示词模板变量（占位符）定义表
 * @TableName ai_prompt_variable
 */
@TableName(value ="ai_prompt_variable")
@Data
public class AiPromptVariable implements SharedEntity {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_prompt.id
     */
    @TableField(value = "prompt_id")
    private Long promptId;

    /**
     * 场景编码冗余，便于按场景检索变量
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 占位符名，不含双花括号，如 question / schema
     */
    @TableField(value = "var_name")
    private String varName;

    /**
     * 占位符中文名
     */
    @TableField(value = "var_label")
    private String varLabel;

    /**
     * 值类型：1 文本、2 数字、3 日期、4 枚举、5 JSON、6 SQL 片段、7 表结构、8 结果集
     */
    @TableField(value = "var_type")
    private Integer varType;

    /**
     * 注入来源：1 系统上下文、2 用户输入、3 元数据召回、4 上一步结果、5 字典/配置
     */
    @TableField(value = "source_type")
    private Integer sourceType;

    /**
     * 来源键，如 user.question / metadata.schema / context.current_date
     */
    @TableField(value = "source_key")
    private String sourceKey;

    /**
     * 是否必填：0 否、1 是；必填变量无值时禁止渲染
     */
    @TableField(value = "required")
    private Integer required;

    /**
     * 默认值，非必填变量缺省时使用
     */
    @TableField(value = "default_value")
    private String defaultValue;

    /**
     * 示例值，调试台与文档展示用
     */
    @TableField(value = "sample_value")
    private String sampleValue;

    /**
     * 值校验正则，为空则不校验
     */
    @TableField(value = "validate_regex")
    private String validateRegex;

    /**
     * 渲染时最大字符数，0 表示不限；用于防止拼接超长 Schema 撑爆上下文
     */
    @TableField(value = "max_length")
    private Integer maxLength;

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
}