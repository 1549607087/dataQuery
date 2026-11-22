package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * P5 问数编排上下文（S0 ~ S9 全程贯穿）。
 *
 * 设计要点：
 * 1. tenantId / userId 在 S0 从登录态与上下文【固化】，后续模型调用、召回、执行、
 *    审计、会话都只读本对象，绝不在异步线程里再取 ThreadLocal。
 * 2. traceId 串起应用日志、ai_model_call_log、ai_query_audit、ai_query_session；
 *    编排器生成后写入 ModelInvocation 与查询审计。
 * 3. schemaContext 只由服务端 S3 召回生成，是 S4 提示词变量与 S6 白名单的唯一来源，
 *    不接受外部注入；前端只能传 question / sessionId / datasourceId / sceneCode。
 * 4. fixRounds 是 SQL_FIX 与 S6/S7 执行失败【共用】的模型修正预算，用完即停。
 */
@Data
public class QueryContext {

    // ---------- S0 固化的身份与归属 ----------
    /** 当前租户（显式固化，异步线程不依赖 ThreadLocal） */
    private Long tenantId;
    /** 提问用户 */
    private Long userId;
    /** 数据源（已通过归属校验） */
    private Long datasourceId;
    /** 会话编号（为空时 S0 新建） */
    private Long sessionId;
    /** 会话业务编号 */
    private String sessionNo;
    /** 全链路追踪 ID */
    private String traceId;
    /** 消息编号（S9 会话更新后回填） */
    private String messageId;

    // ---------- S1 / S2 问题 ----------
    /** 用户原始问题 */
    private String question;
    /** 规范化 / 改写后的问题（S3 Embedding 输入） */
    private String normalizedQuestion;
    /** A 方案追问改写：是否实际改写（供 SSE / 审计观测） */
    private boolean rewriteApplied;
    /** A 方案追问改写：改写来源标识，未改写为 null */
    private String rewriteSource;
    /** A 方案追问改写：上一轮问题原文，未改写为 null */
    private String prevQuestion;
    /** 意图编码：QUERY / FOLLOWUP / CHITCHAT / OUT_OF_SCOPE 等 */
    private String intentCode;
    /** 本次问数场景（默认 SQL_GEN） */
    private String sceneCode = "SQL_GEN";

    // ---------- S3 / S4 ----------
    /** S3 召回结果（同时是 S6 白名单） */
    private SchemaContext schemaContext;
    /** 提示词版本快照编号（写审计） */
    private Long promptVersionId;
    /** 渲染正文哈希（写审计，便于复现） */
    private String promptContentHash;

    // ---------- S5 ----------
    /** 用户在对话页显式指定的模型配置编号（可空；仅作为路由偏好，仍受场景候选集约束） */
    private Long preferredModelConfigId;
    /** 实际使用的模型配置编号 */
    private Long modelConfigId;
    /** 实际使用的模型标识 */
    private String modelId;

    // ---------- S6 / S7 ----------
    /** 模型生成的原始 SQL（可能带 markdown） */
    private String generatedSql;
    /** 校验通过 / 改写后的 SQL */
    private String validatedSql;
    /** 最终执行 SQL */
    private String executedSql;
    /** SQL 校验状态：PASS / REJECT */
    private String sqlStatus;
    /** 校验 / 执行的业务错误码 */
    private Integer errorCode;
    /** 用户可见的错误原因 */
    private String errorMsg;
    /** 已用模型修正次数（SQL_FIX + S6/S7 共用，硬上限由配置提供） */
    private int fixRounds;
    /** 查询审计编号（由 S7 写入后回填，供 S9 会话关联） */
    private Long auditId;
    /**
     * 是否已进入 S7 执行（无论成功 / 拒绝 / 失败）。
     * S7 对三种终态都会 append 一条 ai_query_audit；编排器据此避免重复写终态审计。
     */
    private boolean executionAttempted;

    // ---------- S8 / S9 结果 ----------
    /** 终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED */
    private String terminalState;
    /** 查询执行结果（framework/sqlguard.QueryResult，由调用方装配进响应） */
    private Object queryResult;
    /** 结果摘要（S8，可降级） */
    private String summary;
    /** 降级 / 提示信息（召回降级、只读通道降级、摘要失败等） */
    private String degradeReason;
    /** 端到端开始时间 */
    private long startNanos = System.nanoTime();

    /** 附加扩展变量（前端扩展位，内置变量优先级更高） */
    private Map<String, String> variables = new HashMap<>();

    // ---------- P6：SSE 阶段事件与 S8 规则增强 ----------
    /** 阶段事件输出口；同步 /ask 为 null（零开销），/ask-stream 由 Controller 注入 */
    private ChatStageSink stageSink;
    /** S8 规则图表推荐（可降级为 null） */
    private ChartSpec chartSpec;
    /** S8 结构化口径说明（可降级为 null） */
    private String explain;

    // ---------- P7：对话消息落库 ----------
    /** 对话操作：ASK（普通提问）/ REGENERATE（重新生成）/ EDIT（修改问题） */
    private String messageOp = "ASK";
    /** 操作源消息 id：REGENERATE 时为旧 ASSISTANT 消息，EDIT 时为旧 USER 消息 */
    private Long sourceMessageId;
    /** 操作源消息对象（Controller 已校验归属），供消息落库器作废旧版本 / 继承 seq */
    private cn.iocoder.boot.module.data_query.entity.AiQueryMessage sourceMessage;
    /** 新落库 USER 消息 id（由消息落库器回填，供 SSE 事件携带） */
    private Long userMessageId;
    /** 新落库 ASSISTANT 消息 id */
    private Long assistantMessageId;

    // ---------- 取消令牌 ----------
    /** 本次请求的取消令牌（仅流式 ask 携带；同步/regenerate/edit 为 null） */
    private ChatCancelToken cancelToken;

    /** 阶段边界检查：用户已点停止则抛 CancelledException，由统一异常分支以 STOPPED 收口。 */
    public void checkCancelled() {
        if (cancelToken != null) {
            cancelToken.checkCancelled();
        }
    }

    public int elapsedMs() {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }
}
