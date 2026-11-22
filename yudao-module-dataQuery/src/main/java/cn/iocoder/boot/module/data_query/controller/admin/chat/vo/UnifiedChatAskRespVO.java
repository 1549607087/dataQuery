package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatRespVO;
import cn.iocoder.boot.module.data_query.framework.chat.ChartSpec;
import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeReference;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 统一对话回答 Response VO。
 *
 * <p>公共字段（status / traceId / answer / 计数 / 错误）合并，两种模式各自的专有字段并列。
 * 前端按 mode 渲染不同组件；未使用的专有字段为 null。
 */
@Schema(description = "管理后台 - 统一对话回答 Response VO")
@Data
public class UnifiedChatAskRespVO {

    @Schema(description = "业务类型：QUERY / KNOWLEDGE")
    private String mode;

    @Schema(description = "链路状态：DONE / DEGRADED / CLARIFY / REJECT / FAILED / STOPPED（用户主动停止）")
    private String status;

    @Schema(description = "会话编号")
    private Long sessionId;

    @Schema(description = "会话编号串（KNOWLEDGE）")
    private String sessionNo;

    @Schema(description = "链路编号")
    private String traceId;

    @Schema(description = "本次用户消息编号")
    private Long userMessageId;

    @Schema(description = "本次助手消息编号")
    private Long assistantMessageId;

    @Schema(description = "原始问题")
    private String question;

    @Schema(description = "规范化后的问题")
    private String normalizedQuestion;

    @Schema(description = "最终答案（KNOWLEDGE 为文本回答，QUERY 为摘要）")
    private String answer;

    @Schema(description = "错误码（成功为 null）")
    private Integer errorCode;

    @Schema(description = "错误信息（成功为 null）")
    private String errorMsg;

    @Schema(description = "降级原因（成功为 null）")
    private String degradeReason;

    @Schema(description = "耗时（毫秒）")
    private Integer latencyMs;

    // ==================== QUERY 专有 ====================

    @Schema(description = "生成的 SQL（QUERY）")
    private String sql;

    @Schema(description = "实际执行的 SQL（QUERY）")
    private String executedSql;

    @Schema(description = "查询结果（QUERY）")
    private QueryResult result;

    @Schema(description = "图表规格（QUERY）")
    private ChartSpec chartSpec;

    @Schema(description = "结果解读（QUERY）")
    private String explain;

    @Schema(description = "意图编码（QUERY）")
    private String intentCode;

    @Schema(description = "召回模式（两种模式都有）")
    private String recallMode;

    @Schema(description = "召回表数量（QUERY）")
    private Integer recallTableCount;

    @Schema(description = "数据源编号（QUERY）")
    private Long datasourceId;

    // ==================== KNOWLEDGE 专有 ====================

    @Schema(description = "知识库编号（KNOWLEDGE）")
    private Long baseId;

    @Schema(description = "知识库名称（KNOWLEDGE）")
    private String baseName;

    @Schema(description = "引用溯源列表（KNOWLEDGE）")
    private List<KnowledgeReference> references;

    @Schema(description = "是否降级召回（两种模式都有）")
    private Boolean recallDegraded;

    @Schema(description = "召回条目数（KNOWLEDGE）")
    private Integer retrievedCount;

    @Schema(description = "被裁剪的越界引用数（KNOWLEDGE）")
    private Integer citationDroppedCount;

    // ==================== 转换 ====================

    /** 智能问数响应 → 统一响应 */
    public static UnifiedChatAskRespVO fromQuery(AiChatAskRespVO r) {
        UnifiedChatAskRespVO v = new UnifiedChatAskRespVO();
        v.setMode("QUERY");
        v.setStatus(r.getStatus());
        v.setSessionId(r.getSessionId());
        v.setTraceId(r.getTraceId());
        v.setUserMessageId(r.getUserMessageId());
        v.setAssistantMessageId(r.getAssistantMessageId());
        v.setQuestion(r.getQuestion());
        v.setNormalizedQuestion(r.getNormalizedQuestion());
        v.setAnswer(r.getSummary());
        v.setErrorCode(r.getErrorCode());
        v.setErrorMsg(r.getErrorMsg());
        v.setDegradeReason(r.getDegradeReason());
        v.setLatencyMs(r.getLatencyMs());
        v.setSql(r.getGeneratedSql() != null ? r.getGeneratedSql() : r.getSql());
        v.setExecutedSql(r.getExecutedSql());
        v.setResult(r.getResult());
        v.setChartSpec(r.getChartSpec());
        v.setExplain(r.getExplain());
        v.setIntentCode(r.getIntentCode());
        v.setRecallMode(r.getRecallMode());
        v.setRecallDegraded(r.getRecallDegraded());
        v.setRecallTableCount(r.getRecallTableCount());
        return v;
    }

    /** 知识库问答响应 → 统一响应 */
    public static UnifiedChatAskRespVO fromKnowledge(AiKnowledgeChatRespVO r) {
        UnifiedChatAskRespVO v = new UnifiedChatAskRespVO();
        v.setMode("KNOWLEDGE");
        v.setStatus(r.getStatus());
        v.setSessionId(r.getSessionId());
        v.setSessionNo(r.getSessionNo());
        v.setTraceId(r.getTraceId());
        v.setUserMessageId(r.getUserMessageId());
        v.setAssistantMessageId(r.getAssistantMessageId());
        v.setQuestion(r.getQuestion());
        v.setAnswer(r.getAnswer());
        v.setErrorCode(r.getErrorCode());
        v.setErrorMsg(r.getErrorMsg());
        v.setDegradeReason(r.getDegradeReason());
        v.setLatencyMs(r.getLatencyMs());
        v.setBaseId(r.getBaseId());
        v.setBaseName(r.getBaseName());
        v.setReferences(r.getReferences());
        v.setRecallMode(r.getRecallMode());
        v.setRecallDegraded(r.getRecallDegraded());
        v.setRetrievedCount(r.getRetrievedCount());
        v.setCitationDroppedCount(r.getCitationDroppedCount());
        return v;
    }
}
