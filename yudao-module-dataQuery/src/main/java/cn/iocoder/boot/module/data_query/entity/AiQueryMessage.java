package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问数对话消息（ai_query_message，P7）
 *
 * 租户私有表：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id；写入时必须显式 setTenantId。
 *
 * 一条消息 = 一个对话轮次的一侧：
 * - USER：question 为用户问题，parent_id=0；
 * - ASSISTANT：parent_id 指向对应 USER，answer / sql / result_json 等为回答快照。
 * 主链 = is_active=1 且按 seq 升序；重新生成 / 修改问题产生新版本，旧版本 is_active=0 保留可追溯。
 */
@TableName(value = "ai_query_message")
@Data
public class AiQueryMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    public static final Integer RATING_NONE = 0;
    public static final Integer RATING_UP = 1;
    public static final Integer RATING_DOWN = -1;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联 ai_query_session.id */
    @TableField(value = "session_id")
    private Long sessionId;

    /** 消息角色：USER / ASSISTANT */
    @TableField(value = "role")
    private String role;

    /** 父消息 id：ASSISTANT 指向对应 USER；USER 为 0 */
    @TableField(value = "parent_id")
    private Long parentId;

    /** 所在分支根 USER 消息 id，便于按分支聚合 */
    @TableField(value = "root_message_id")
    private Long rootMessageId;

    /** 版本：同一问题重新生成/修改后递增 */
    @TableField(value = "version")
    private Integer version;

    /** 会话内展示序号 */
    @TableField(value = "seq")
    private Integer seq;

    /** 是否当前主链：1 是 / 0 历史版本 */
    @TableField(value = "is_active")
    private Integer isActive;

    /** 用户问题（USER 消息） */
    @TableField(value = "question")
    private String question;

    /** 助手文本回答 / 摘要（ASSISTANT 消息） */
    @TableField(value = "answer")
    private String answer;

    @TableField(value = "generated_sql")
    private String generatedSql;

    @TableField(value = "executed_sql")
    private String executedSql;

    /** QueryResult 结果快照 JSON（只读执行结果，不含连接串/凭证） */
    @TableField(value = "result_json")
    private String resultJson;

    @TableField(value = "chart_spec_json")
    private String chartSpecJson;

    @TableField(value = "explain")
    private String explain;

    /** 终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED / STOPPED（用户主动停止） */
    @TableField(value = "status")
    private String status;

    @TableField(value = "error_code")
    private String errorCode;

    @TableField(value = "error_msg")
    private String errorMsg;

    @TableField(value = "trace_id")
    private String traceId;

    /** 关联 ai_query_audit.id */
    @TableField(value = "audit_id")
    private Long auditId;

    @TableField(value = "datasource_id")
    private Long datasourceId;

    /** 赞踩：-1 踩 / 0 未评价 / 1 赞（仅 ASSISTANT 有效） */
    @TableField(value = "rating")
    private Integer rating;

    @TableField(value = "tenant_id")
    private Long tenantId;

    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "creator")
    private String creator;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "updater")
    private String updater;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(value = "deleted")
    private Integer deleted;

}
