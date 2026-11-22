package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.ChatMessageRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.KnowledgeChatMessageRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 统一对话消息 Response VO。
 *
 * <p>两种模式的消息字段并集，前端用 role 区分 USER/ASSISTANT、按 mode 渲染专有字段。
 * mode=QUERY 时 sql/result/chartSpec 有效；mode=KNOWLEDGE 时 references 有效。
 */
@Schema(description = "管理后台 - 统一对话消息 Response VO")
@Data
public class UnifiedChatMessageRespVO {

    @Schema(description = "业务类型：QUERY / KNOWLEDGE")
    private String mode;

    @Schema(description = "消息编号")
    private Long id;

    @Schema(description = "角色：USER / ASSISTANT")
    private String role;

    @Schema(description = "父消息编号")
    private Long parentId;

    @Schema(description = "版本")
    private Integer version;

    @Schema(description = "序号")
    private Integer seq;

    @Schema(description = "是否当前分支选中")
    private Boolean active;

    @Schema(description = "问题文本")
    private String question;

    @Schema(description = "回答文本")
    private String answer;

    @Schema(description = "赞踩：-1 / 0 / 1")
    private Integer rating;

    @Schema(description = "链路编号")
    private String traceId;

    @Schema(description = "终态")
    private String status;

    @Schema(description = "错误码")
    private Integer errorCode;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // ==================== QUERY 专有 ====================

    @Schema(description = "生成 SQL（QUERY）")
    private String generatedSql;

    @Schema(description = "执行 SQL（QUERY）")
    private String executedSql;

    @Schema(description = "查询结果（QUERY）")
    private Object result;

    @Schema(description = "图表规格（QUERY）")
    private Object chartSpec;

    @Schema(description = "结果解读（QUERY）")
    private String explain;

    @Schema(description = "审计编号（QUERY）")
    private Long auditId;

    // ==================== KNOWLEDGE 专有 ====================

    @Schema(description = "引用溯源（KNOWLEDGE）")
    private Object references;

    @Schema(description = "召回模式（KNOWLEDGE）")
    private String recallMode;

    @Schema(description = "是否降级召回（KNOWLEDGE）")
    private Boolean recallDegraded;

    @Schema(description = "召回条目数（KNOWLEDGE）")
    private Integer retrievedCount;

    @Schema(description = "被裁剪引用数（KNOWLEDGE）")
    private Integer citationDroppedCount;

    @Schema(description = "耗时（毫秒，KNOWLEDGE）")
    private Integer latencyMs;

    public static UnifiedChatMessageRespVO fromQuery(ChatMessageRespVO m) {
        UnifiedChatMessageRespVO v = new UnifiedChatMessageRespVO();
        v.setMode("QUERY");
        v.setId(m.getId());
        v.setRole(m.getRole());
        v.setParentId(m.getParentId());
        v.setVersion(m.getVersion());
        v.setSeq(m.getSeq());
        v.setActive(m.getActive());
        v.setQuestion(m.getQuestion());
        v.setAnswer(m.getAnswer());
        v.setRating(m.getRating());
        v.setTraceId(m.getTraceId());
        v.setStatus(m.getStatus());
        v.setErrorCode(parseCode(m.getErrorCode()));
        v.setErrorMsg(m.getErrorMsg());
        v.setCreateTime(m.getCreateTime());
        v.setGeneratedSql(m.getGeneratedSql());
        v.setExecutedSql(m.getExecutedSql());
        v.setResult(m.getResult());
        v.setChartSpec(m.getChartSpec());
        v.setExplain(m.getExplain());
        v.setAuditId(m.getAuditId());
        return v;
    }

    public static UnifiedChatMessageRespVO fromKnowledge(KnowledgeChatMessageRespVO m) {
        UnifiedChatMessageRespVO v = new UnifiedChatMessageRespVO();
        v.setMode("KNOWLEDGE");
        v.setId(m.getId());
        v.setRole(m.getRole());
        v.setParentId(m.getParentId());
        v.setVersion(m.getVersion());
        v.setSeq(m.getSeq());
        v.setActive(m.getActive());
        v.setQuestion(m.getQuestion());
        v.setAnswer(m.getAnswer());
        v.setRating(m.getRating());
        v.setTraceId(m.getTraceId());
        v.setStatus(m.getStatus());
        v.setErrorCode(parseCode(m.getErrorCode()));
        v.setErrorMsg(m.getErrorMsg());
        v.setCreateTime(m.getCreateTime());
        v.setReferences(m.getReferences());
        v.setRecallMode(m.getRecallMode());
        v.setRecallDegraded(m.getRecallDegraded());
        v.setRetrievedCount(m.getRetrievedCount());
        v.setCitationDroppedCount(m.getCitationDroppedCount());
        v.setLatencyMs(m.getLatencyMs());
        return v;
    }

    private static Integer parseCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(code);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
