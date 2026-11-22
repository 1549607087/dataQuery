package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈（ai_query_feedback）
 *
 * 问数运行时 4 张表（P2-B）全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_query_feedback")
@Data
public class AiQueryFeedback {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_query_audit.id，本次反馈对应的问答
     */
    @TableField(value = "audit_id")
    private Long auditId;

    /**
     * 关联 ai_query_session.id
     */
    @TableField(value = "session_id")
    private Long sessionId;

    /**
     * 反馈用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 评分：1~5，5 为最满意
     */
    @TableField(value = "rating")
    private Integer rating;

    /**
     * 反馈类型：如 结果不准 / SQL 错误 / 口径疑问 / 其它
     */
    @TableField(value = "feedback_type")
    private String feedbackType;

    /**
     * 用户文字反馈
     */
    @TableField(value = "comment")
    private String comment;

    /**
     * 用户纠正后的 SQL，仅作参考；反馈不自动改写线上提示词版本，人工审核后才可进 few-shot
     */
    @TableField(value = "corrected_sql")
    private String correctedSql;

    /**
     * 审核状态：0 待审、1 已采纳、2 已驳回
     */
    @TableField(value = "reviewed")
    private Integer reviewed;

    /**
     * 审核人用户 ID
     */
    @TableField(value = "reviewer_id")
    private Long reviewerId;

    /**
     * 审核时间
     */
    @TableField(value = "review_time")
    private LocalDateTime reviewTime;

    /**
     * 审核备注
     */
    @TableField(value = "review_remark")
    private String reviewRemark;

    /**
     * 租户私有，全表不登记 ignore-tables，写入显式 setTenantId
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * creator
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * create_time
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * updater
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * update_time
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * deleted
     */
    @TableField(value = "deleted")
    private Integer deleted;

}
