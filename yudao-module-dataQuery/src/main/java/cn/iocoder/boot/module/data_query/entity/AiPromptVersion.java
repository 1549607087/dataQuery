package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

import cn.iocoder.boot.module.data_query.framework.tenant.SharedEntity;

/**
 * 提示词版本表（内容快照）
 * @TableName ai_prompt_version
 */
@TableName(value ="ai_prompt_version")
@Data
public class AiPromptVersion implements SharedEntity {
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
     * 版本号，同一提示词内单调递增
     */
    @TableField(value = "version")
    private Integer version;

    /**
     * 该版本的模板正文快照
     */
    @TableField(value = "content")
    private String content;

    /**
     * 该版本的变量定义快照，用于版本 diff
     */
    @TableField(value = "variable_defs")
    private String variableDefs;

    /**
     * 该版本的模型参数快照
     */
    @TableField(value = "params")
    private String params;

    /**
     * 变更说明
     */
    @TableField(value = "change_log")
    private String changeLog;

    /**
     * 正文哈希，用于快速判断两版本是否相同
     */
    @TableField(value = "content_hash")
    private String contentHash;

    /**
     * Token 预估，调试台展示用
     */
    @TableField(value = "token_estimate")
    private Integer tokenEstimate;

    /**
     * 状态：0 草稿、1 已发布、2 已回滚、3 归档
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 发布时间
     */
    @TableField(value = "publish_time")
    private LocalDateTime publishTime;

    /**
     * 发布人
     */
    @TableField(value = "publisher")
    private String publisher;

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