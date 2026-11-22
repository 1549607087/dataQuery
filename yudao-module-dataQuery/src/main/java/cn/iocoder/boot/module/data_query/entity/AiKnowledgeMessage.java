package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库问答消息（ai_knowledge_message，P9.2）
 *
 * <p>租户私有表：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore；
 * 写入时必须显式 setTenantId。
 *
 * <p>对话树语义与问数消息 {@link AiQueryMessage} 一致：
 * <ul>
 *   <li>USER：question 为用户问题，parent_id 指向上一轮 ASSISTANT（首条为 NULL 根）；</li>
 *   <li>ASSISTANT：parent_id 指向对应 USER，answer / references_json 为回答快照；</li>
 *   <li>主链 = current_leaf_id 沿 parent_id 回溯的路径；is_active 只表达兄弟组内是否选中。</li>
 * </ul>
 * 与问数消息的差异：去掉 SQL / 审计 / 图表类字段，新增 references_json 与召回质量指标。
 */
@TableName(value = "ai_knowledge_message")
@Data
public class AiKnowledgeMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    public static final Integer RATING_NONE = 0;
    public static final Integer RATING_UP = 1;
    public static final Integer RATING_DOWN = -1;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联 ai_knowledge_session.id */
    @TableField(value = "session_id")
    private Long sessionId;

    /** 消息角色：USER / ASSISTANT */
    @TableField(value = "role")
    private String role;

    /** 直接前驱消息 id：ASSISTANT 指向 USER，USER 根节点为 NULL */
    @TableField(value = "parent_id")
    private Long parentId;

    /** 所在分支根 USER 消息 id */
    @TableField(value = "root_message_id")
    private Long rootMessageId;

    /** 同一 parent + role 下的版本号，重新生成 / 修改问题时递增 */
    @TableField(value = "version")
    private Integer version;

    /** 会话内展示序号；ASK 新轮 = 会话 MAX(seq)+1 */
    @TableField(value = "seq")
    private Integer seq;

    /** 兄弟组内是否选中：1 是 / 0 历史版本 */
    @TableField(value = "is_active")
    private Integer isActive;

    /** 用户问题（USER 消息） */
    @TableField(value = "question")
    private String question;

    /** 助手文本回答（ASSISTANT 消息） */
    @TableField(value = "answer")
    private String answer;

    /** 引用溯源 JSON 数组 */
    @TableField(value = "references_json")
    private String referencesJson;

    /** 本轮 RAG 提示词版本快照 id */
    @TableField(value = "prompt_version_id")
    private Long promptVersionId;

    /** 实际使用的模型标识快照 */
    @TableField(value = "model_id")
    private String modelId;

    /** 实际使用的模型配置编号快照 */
    @TableField(value = "model_config_id")
    private Long modelConfigId;

    /** 召回方式：VECTOR / HYBRID / KEYWORD_DEGRADED / EMPTY */
    @TableField(value = "recall_mode")
    private String recallMode;

    /** 是否降级召回：1 是 / 0 否 */
    @TableField(value = "recall_degraded")
    private Integer recallDegraded;

    /** 本轮进入上下文的切片数（裁剪后） */
    @TableField(value = "retrieved_count")
    private Integer retrievedCount;

    /** 回答中被裁剪的越界引用编号数量 */
    @TableField(value = "citation_dropped_count")
    private Integer citationDroppedCount;

    /** 本轮耗时（毫秒） */
    @TableField(value = "latency_ms")
    private Integer latencyMs;

    /** 终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED / STOPPED（用户主动停止） */
    @TableField(value = "status")
    private String status;

    @TableField(value = "error_code")
    private String errorCode;

    @TableField(value = "error_msg")
    private String errorMsg;

    @TableField(value = "trace_id")
    private String traceId;

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
