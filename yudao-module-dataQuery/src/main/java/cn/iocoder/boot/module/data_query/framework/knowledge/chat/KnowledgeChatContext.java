package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelToken;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeRecallResult;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识库问答编排上下文（RAG S0 ~ S6 全程贯穿）
 *
 * <p>与问数 {@code QueryContext} 同范式：身份、归属、链路在 S0 一次性【固化】，
 * 后续召回 / 提示词 / 模型调用 / 落库只读本对象，不在任何环节重新取 ThreadLocal。
 *
 * <p>三个不可外部注入的字段（安全边界）：
 * <ul>
 *   <li>{@code baseId}：S0 由前端传入但必须经知识库归属校验，会话内不可切换；</li>
 *   <li>{@code recallResult}：只由服务端 {@code KnowledgeRecallService} 生成，
 *       contextText 是提示词 variables.context 的唯一来源，不接受前端传上下文；</li>
 *   <li>{@code historyText}：只由服务端对话树主链回溯生成，不接受前端传历史。</li>
 * </ul>
 */
@Data
public class KnowledgeChatContext {

    // ---------- S0 固化的身份与归属 ----------
    /** 当前租户（显式固化，后续不依赖 ThreadLocal） */
    private Long tenantId;
    /** 提问用户 */
    private Long userId;
    /** 知识库编号（已通过归属校验，会话内不可切换） */
    private Long baseId;
    /** 知识库名称（提示词变量 / 事件透出用） */
    private String baseName;
    /** 会话编号 */
    private Long sessionId;
    /** 会话业务编号 KS... */
    private String sessionNo;
    /** 全链路追踪 ID */
    private String traceId;
    /** 场景编码，固定 RAG */
    private String sceneCode = "RAG";

    // ---------- S1 问题 ----------
    /** 用户原始问题 */
    private String question;
    /** 规范化后的问题（召回 Embedding 输入；去空白、去首尾） */
    private String normalizedQuestion;

    // ---------- S2 历史拼装 ----------
    /** 主链历史的纯文本（「用户：… \n助手：…」），注入提示词 variables.history */
    private String historyText;
    /** 实际纳入历史的轮次 */
    private Integer historyTurns;

    // ---------- S3 召回 ----------
    /** 召回结果（contextText 已带 [1..K] 连续编号） */
    private KnowledgeRecallResult recallResult;

    // ---------- S4 提示词 ----------
    /** 提示词版本快照编号 */
    private Long promptVersionId;
    /** 渲染正文哈希 */
    private String promptContentHash;

    // ---------- S5 模型 ----------
    /** 用户在对话页指定的模型配置编号（仅路由偏好，仍受 RAG 场景候选集约束） */
    private Long preferredModelConfigId;
    /** 实际使用的模型配置编号 */
    private Long modelConfigId;
    /** 实际使用的模型标识 */
    private String modelId;

    // ---------- S6 回答与引用 ----------
    /** 模型原始回答 */
    private String rawAnswer;
    /** 引用校验后的回答（越界编号已删除） */
    private String answer;
    /** 引用校验结果（仅被引用切片） */
    private CitationValidator.CitationCheckResult citation;
    /** references_json（落库快照） */
    private String referencesJson;
    /** 被删除的越界编号数量 */
    private Integer citationDroppedCount = 0;

    // ---------- 终态与错误 ----------
    /** 终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED */
    private String terminalState;
    /** 业务错误码 */
    private Integer errorCode;
    /** 用户可见原因 */
    private String errorMsg;
    /** 降级 / 提示信息（召回降级、引用裁剪等） */
    private String degradeReason;

    // ---------- 对话树落库 ----------
    /** 对话操作：ASK / REGENERATE / EDIT（本轮仅 ASK 生效，其余预留） */
    private String messageOp = "ASK";
    /** 操作源消息 id */
    private Long sourceMessageId;
    /** 操作源消息对象（Controller 已校验归属） */
    private AiKnowledgeMessage sourceMessage;
    /** 新落库 USER 消息 id（写入器回填） */
    private Long userMessageId;
    /** 新落库 ASSISTANT 消息 id（写入器回填；未落库时为 null） */
    private Long assistantMessageId;
    /** 新落库 ASSISTANT 的兄弟版本号（写入器回填，供前端显示「第 N 版」） */
    private Integer assistantVersion;

    // ---------- 运行时 ----------
    /** 阶段事件输出口；同步 /ask 为 null（零开销），SSE 由 Controller 注入 */
    private ChatStageSink stageSink;
    /** 附加扩展变量（前端扩展位，内置变量优先级更高） */
    private Map<String, String> variables = new HashMap<>();
    /** 端到端开始时间 */
    private long startNanos = System.nanoTime();
    /** 本次请求的取消令牌（仅流式 ask 携带；同步/regenerate/edit 为 null） */
    private ChatCancelToken cancelToken;
    /** 增量序号：同一请求内从 1 递增，前端据此丢弃乱序/重复帧 */
    private final AtomicInteger deltaSeq = new AtomicInteger();

    // ---------- 流式可观测（只用于事件 payload 与日志，不落库） ----------
    /** 是否走了流式模型调用；为 true 时终态才输出 stream 观测日志 */
    private boolean streaming;
    /** 首个非空增量到达的时刻（nanoTime）；0 表示尚未收到 */
    private volatile long firstTokenNanos;
    /** 已下发给客户端的正文字符数（门控输出口径，与前端所见一致） */
    private volatile int pushedChars;
    /** 终态归因：USER_CANCEL / CLIENT_GONE / TIMEOUT，同步链路为 null */
    private String stopReason;

    /** 取下一个增量序号 */
    public int nextDeltaSeq() {
        return deltaSeq.incrementAndGet();
    }

    /** 记录首 token 时刻；只有第一次生效。调用点必须在引用门控之前，否则 holdback 会污染 TTFT。 */
    public void markFirstToken() {
        if (firstTokenNanos == 0L) {
            firstTokenNanos = System.nanoTime();
        }
    }

    /** 首 token 耗时（毫秒）；未收到任何增量时返回 -1 */
    public int ttftMs() {
        long first = firstTokenNanos;
        return first == 0L ? -1 : (int) ((first - startNanos) / 1_000_000L);
    }

    /** 已下发的增量帧数 */
    public int deltaCount() {
        return deltaSeq.get();
    }

    /** 阶段边界检查：用户已点停止则抛 CancelledException，由统一异常分支以 STOPPED 收口。 */
    public void checkCancelled() {
        if (cancelToken != null) {
            cancelToken.checkCancelled();
        }
    }

    public int elapsedMs() {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }

    /** 召回是否降级（供落库 recall_degraded 使用） */
    public boolean isRecallDegraded() {
        return recallResult != null && Boolean.TRUE.equals(recallResult.getDegraded());
    }

    /** 进入上下文的切片数（裁剪后） */
    public Integer retrievedCount() {
        return recallResult == null ? 0 : recallResult.getKeptCount();
    }
}
