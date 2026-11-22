package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.boot.module.data_query.framework.chat.CancelledException;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelContext;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelToken;
import cn.iocoder.boot.module.data_query.framework.chat.ChatDeltaEvent;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageEvent;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeRecallResult;
import cn.iocoder.boot.module.data_query.framework.model.ModelClient;
import cn.iocoder.boot.module.data_query.framework.model.ModelErrorCategory;
import cn.iocoder.boot.module.data_query.framework.model.ModelException;
import cn.iocoder.boot.module.data_query.framework.model.ModelGovernanceExecutor;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocation;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocationResult;
import cn.iocoder.boot.module.data_query.framework.model.StreamAbortSignal;
import cn.iocoder.boot.module.data_query.framework.model.StreamModelClient;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
import cn.iocoder.boot.module.data_query.service.KnowledgeConversationService;
import cn.iocoder.boot.module.data_query.service.KnowledgeRecallService;
import cn.iocoder.boot.module.data_query.service.KnowledgeSessionService;
import cn.iocoder.boot.module.data_query.service.PromptRenderService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_QUESTION_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_QUESTION_TOO_LONG;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_BASE_DISABLED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_BASE_ID_REQUIRED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_EMPTY_ANSWER;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_MODEL_CALL_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_MODEL_NOT_READY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_PROMPT_INJECTION;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_RECALL_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_SESSION_BASE_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_SESSION_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库问答编排器（RAG S0 ~ S6）
 *
 * <p>链路：
 * <pre>
 * S0 建上下文     身份 / 知识库归属 / 会话（复用或新建）
 * S1 历史拼装     主链最近 N 条，按 Token 预算裁剪 → variables.history
 * S2 混合召回     向量 + 关键词 + RRF + Token 裁剪 → 带 [1..K] 编号的 contextText
 * S3 提示词装配   sceneCode=RAG，context / history 只由服务端注入
 * S4 受治理调用   ModelGovernanceExecutor（限流 / 熔断 / 超时 / 重试 / 降级）
 * S5 引用校验     越界编号删除，只保留真实引用的切片
 * S6 对话树落库   会话行锁内落 USER + ASSISTANT 并推进叶子
 * </pre>
 *
 * <p><b>安全红线（代码硬约束，不可配置关闭）</b>：
 * <ol>
 *   <li>知识库归属：S0 校验 base 属于当前租户且未停用，跨租户一律按「不存在」处理；</li>
 *   <li>上下文来源：提示词里的 context / history 只由服务端 S1 / S2 生成，
 *       PromptRenderReqVO 的 ragContext / ragHistory 带 {@code @JsonIgnore}，
 *       前端即使构造同名字段也不会被反序列化，杜绝伪造检索证据；</li>
 *   <li>会话不可换库：会话绑定的 base 与请求 base 不一致直接拒绝，
 *       否则历史上下文会与当前知识库口径混杂；</li>
 *   <li>引用不可凭空产生：references 只来自本轮真实进入上下文的切片，模型编造的编号被删除。</li>
 * </ol>
 *
 * <p><b>本期范围</b>：会话 + 同步问答。重新生成 / 修改问题 / 赞踩 / SSE 由下一期接入，
 * 表结构与上下文字段已预留（messageOp / sourceMessage / stageSink），不需要改表。
 */
@Slf4j
@Service
public class KnowledgeChatOrchestrator {

    /** RAG 场景编码：路由、提示词、模型绑定均以此为场景键 */
    public static final String SCENE_RAG = "RAG";

    // 终态（与问数同口径，前端可复用同一套状态渲染）
    private static final String STATE_DONE = "DONE";
    private static final String STATE_CLARIFY = "CLARIFY";
    private static final String STATE_REJECT = "REJECT";
    private static final String STATE_DEGRADED = "DEGRADED";
    private static final String STATE_FAILED = "FAILED";
    private static final String STATE_STOPPED = "STOPPED";

    /** 注入检测特征（与问数同表；命中即拒绝，宁可澄清也不硬答） */
    private static final List<String> INJECTION_PATTERNS = List.of(
            "忽略以上", "忽略之前", "忽略上面的", "忽略上述", "无视以上",
            "ignore previous", "ignore above", "ignore all previous",
            "系统提示", "system prompt", "开发者模式", "developer mode",
            "越狱", "jailbreak", "你现在是", "you are now",
            "输出你的提示", "reveal your", "打印提示词");

    @Resource
    private KnowledgeProperties properties;
    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;
    @Resource
    private KnowledgeSessionService sessionService;
    @Resource
    private KnowledgeConversationService conversationService;
    @Resource
    private KnowledgeRecallService recallService;
    @Resource
    private PromptRenderService promptRenderService;
    @Resource
    private ModelGovernanceExecutor modelGovernanceExecutor;
    @Resource
    private ModelClient modelClient;
    /** 流式 SPI：同步链路不依赖；缺实现时（理论上不会）自动退回同步调用 */
    @Resource
    private StreamModelClient streamModelClient;
    @Resource
    private CitationValidator citationValidator;
    @Resource
    private KnowledgeMessageTreeWriter messageTreeWriter;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 同步知识库问答：一次调用走完 S0 ~ S6。
     *
     * <p>S0 的参数 / 归属类异常直接向上抛（此时会话可能尚未创建，前端需要明确的参数错误）；
     * S0 之后的所有异常都转成可渲染的终态，保证「用户提过的问题」在会话里始终留痕。
     */
    public AiKnowledgeChatRespVO ask(AiKnowledgeChatAskReqVO reqVO) {
        return askWithMessageOp(reqVO, null, KnowledgeMessageTreeWriter.OP_ASK, null);
    }

    /**
     * 带阶段事件输出口的问答（SSE）：与同步 /ask 共用同一实现，sink 为 null 时零开销。
     */
    public AiKnowledgeChatRespVO ask(AiKnowledgeChatAskReqVO reqVO, ChatStageSink sink) {
        return askWithMessageOp(reqVO, sink, KnowledgeMessageTreeWriter.OP_ASK, null);
    }

    /**
     * 带对话操作元数据的问答（重新生成 / 修改问题）：由门面校验归属后调用。
     * 业务链路仍走同一实现，只在 S0 前把操作类型与源消息固化进上下文。
     */
    public AiKnowledgeChatRespVO ask(AiKnowledgeChatAskReqVO reqVO, String messageOp,
                                     cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage sourceMessage) {
        return ask(reqVO, null, messageOp, sourceMessage);
    }

    /**
     * 重跑类入口（流式 / 同步共用）：sink 非 null 时 S4 走流式模型调用，其余阶段完全一致。
     */
    public AiKnowledgeChatRespVO ask(AiKnowledgeChatAskReqVO reqVO, ChatStageSink sink, String messageOp,
                                     cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage sourceMessage) {
        return askWithMessageOp(reqVO, sink, messageOp, sourceMessage);
    }

    /** 真正的问答实现：messageOp / sourceMessage / sink 仅在对应入口非默认值 */
    private AiKnowledgeChatRespVO askWithMessageOp(AiKnowledgeChatAskReqVO reqVO, ChatStageSink sink,
                                                   String messageOp,
                                                   cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage sourceMessage) {
        KnowledgeChatContext ctx = new KnowledgeChatContext();
        ctx.setStageSink(sink);
        // 流式 ask 在 Controller 里把取消令牌放入 ChatCancelContext；同步/regenerate/edit 为 null
        ctx.setCancelToken(ChatCancelContext.current());
        if (messageOp != null) {
            ctx.setMessageOp(messageOp);
        }
        if (sourceMessage != null) {
            ctx.setSourceMessage(sourceMessage);
            ctx.setSourceMessageId(sourceMessage.getId());
        }
        AiKnowledgeChatRespVO resp = new AiKnowledgeChatRespVO();
        emit(ctx, "intent", ChatStageEvent.RUNNING, "正在理解问题", 5,
                e -> e.put("question", reqVO.getQuestion()));
        try {
            // ==================== S0 建立上下文 ====================
            s0Context(ctx, reqVO);
            resp.setSessionId(ctx.getSessionId());
            resp.setSessionNo(ctx.getSessionNo());
            resp.setTraceId(ctx.getTraceId());
            resp.setBaseId(ctx.getBaseId());
            resp.setBaseName(ctx.getBaseName());
            resp.setQuestion(ctx.getQuestion());

            // ==================== S1 注入检测 + 历史拼装 ====================
            // 检测放在 S0 之后：会话已建立，被拒绝的提问也能在会话里留痕（问题 + 拒绝说明）
            if (Boolean.TRUE.equals(properties.getPromptInjectionGuardEnabled()) && isInjection(ctx.getQuestion())) {
                ctx.setErrorCode(KNOWLEDGE_CHAT_PROMPT_INJECTION.getCode());
                ctx.setErrorMsg(KNOWLEDGE_CHAT_PROMPT_INJECTION.getMsg());
                ctx.setAnswer("该提问包含疑似指令注入的内容，已拒绝处理。如需查询知识，请直接描述业务问题。");
                return finish(ctx, resp, STATE_REJECT);
            }
            int historyMax = properties.getHistoryMaxMessages() == null ? 6 : properties.getHistoryMaxMessages();
            int historyBudget = properties.getHistoryTokenBudget() == null ? 1500 : properties.getHistoryTokenBudget();
            KnowledgeConversationService.HistoryResult history =
                    conversationService.buildHistory(ctx.getSessionId(), historyMax, historyBudget);
            if (!history.isEmpty()) {
                ctx.setHistoryText(history.getText());
                ctx.setHistoryTurns(history.getTurnCount());
            }
            emit(ctx, "context", ChatStageEvent.DONE, "已准备对话上下文", 20,
                    e -> e.put("historyTurns", ctx.getHistoryTurns() == null ? 0 : ctx.getHistoryTurns()));

            // ==================== S2 混合召回 ====================
            KnowledgeRecallResult recall = recallService.recall(
                    ctx.getBaseId(), ctx.getNormalizedQuestion(), properties.getTopK(), reqVO.getVersion());
            ctx.setRecallResult(recall);
            emit(ctx, "recalled", ChatStageEvent.DONE, "检索到 " + ctx.retrievedCount() + " 条相关资料", 45,
                    e -> e.put("mode", recall.getMode())
                            .put("keptCount", recall.getKeptCount())
                            .put("degraded", recall.getDegraded())
                            .put("truncated", recall.getTruncated()));
            if (recall.getItems() == null || recall.getItems().isEmpty()) {
                // 召回为空 → CLARIFY：不把空上下文丢给模型编答案
                ctx.setErrorCode(KNOWLEDGE_CHAT_RECALL_EMPTY.getCode());
                ctx.setErrorMsg(KNOWLEDGE_CHAT_RECALL_EMPTY.getMsg());
                ctx.setAnswer(buildClarifyAnswer(ctx));
                return finish(ctx, resp, STATE_CLARIFY);
            }

            // ==================== S3 提示词装配 ====================
            PromptRenderRespVO prompt = renderPrompt(ctx);
            ctx.setPromptVersionId(prompt.getPromptVersionId());
            ctx.setPromptContentHash(prompt.getContentHash());

            // ==================== S4 受治理的模型调用 ====================
            ctx.checkCancelled();
            emit(ctx, "generated", ChatStageEvent.RUNNING, "模型正在生成回答", 65);
            // 有阶段输出口（/ask-stream）且有流式实现 → 逐增量下发；否则同步整段（/ask 行为完全不变）
            StreamingCitationGate gate = (ctx.getStageSink() != null && streamModelClient != null)
                    ? new StreamingCitationGate(recall.getItems(), maxAnswerChars())
                    : null;
            ModelInvocationResult modelResult;
            try {
                modelResult = invokeModel(ctx, prompt, gate);
            } catch (RuntimeException ex) {
                if (gate != null && StringUtils.hasText(gate.answer())) {
                    // 已推送的文本不能因为后面失败就消失：用户已经看到了，落库必须与之一致
                    ctx.setAnswer(gate.answer());
                }
                throw ex;
            }
            ctx.setModelId(modelResult.getModelId());
            ctx.setModelConfigId(modelResult.getModelConfigId());
            if (Boolean.TRUE.equals(modelResult.getEcho())) {
                // EchoModelClient：绝不把回声当业务回答
                ctx.setErrorCode(KNOWLEDGE_CHAT_MODEL_NOT_READY.getCode());
                ctx.setErrorMsg(KNOWLEDGE_CHAT_MODEL_NOT_READY.getMsg());
                ctx.setAnswer(buildModelNotReadyAnswer(ctx));
                return finish(ctx, resp, STATE_DEGRADED);
            }
            String rawAnswer = modelResult.getContent() == null ? "" : modelResult.getContent().trim();
            ctx.setRawAnswer(rawAnswer);
            // 主动截断时客户端返回的内容可能为空（自定义客户端兜底分支），
            // 真正正文在门控里，不能按「模型返回空回答」判失败。
            if (!StringUtils.hasText(rawAnswer) && !Boolean.TRUE.equals(modelResult.getAborted())) {
                ctx.setErrorCode(KNOWLEDGE_CHAT_EMPTY_ANSWER.getCode());
                ctx.setErrorMsg(KNOWLEDGE_CHAT_EMPTY_ANSWER.getMsg());
                ctx.setAnswer(KNOWLEDGE_CHAT_EMPTY_ANSWER.getMsg());
                return finish(ctx, resp, STATE_FAILED);
            }

            // ==================== S5 引用校验 ====================
            CitationValidator.CitationCheckResult check;
            if (gate != null) {
                // 流式：门控已在生成过程中完成「删越界 + 全角归一 + 记账」，这里只放行尾巴并收口。
                // 绝不能再跑一次 validate() 重新清洗——那会让刷新后的答案与刚才看到的不一致。
                emitDelta(ctx, gate.flush());
                if (gate.isTruncated()) {
                    ctx.setDegradeReason("回答超过 " + maxAnswerChars() + " 字上限，已截断");
                }
                ctx.setAnswer(gate.answer());
                check = gate.toCheckResult();
            } else {
                check = citationValidator.validate(rawAnswer, recall.getItems());
                ctx.setAnswer(clip(check.getCleanedAnswer(), maxAnswerChars()));
            }
            ctx.setCitation(check);
            ctx.setCitationDroppedCount(check.getDroppedCount());
            ctx.setReferencesJson(toJson(check.getReferences()));
            if (check.getDroppedCount() != null && check.getDroppedCount() > 0) {
                ctx.setDegradeReason("模型回答中有 " + check.getDroppedCount() + " 处引用编号不在本次检索结果范围内，已删除");
            }
            emit(ctx, "validated", ChatStageEvent.DONE, "引用校验完成", 90,
                    e -> e.put("referenceCount", ctx.getCitation().getReferences().size())
                            .put("dropped", check.getDroppedCount()));
            return finish(ctx, resp, STATE_DONE);
        } catch (CancelledException ex) {
            // 用户主动停止 / 客户端断连 / 连接超时：都以 STOPPED 收口，会话已建立（取消只发生在 S4 之后）
            String reason = resolveStopReason(ctx);
            ctx.setStopReason(reason);
            log.info("[knowledge-chat][cancelled] traceId={}, clientRequestId={}, reason={}",
                    ctx.getTraceId(), ex.getClientRequestId(), reason);
            ctx.setErrorMsg(stopMessage(reason));
            // 已推送的部分文本优先保留：用户看到的内容与库内必须一致
            ctx.setAnswer(StringUtils.hasText(ctx.getAnswer()) ? ctx.getAnswer() : stopMessage(reason));
            return finish(ctx, resp, STATE_STOPPED);
        } catch (ServiceException ex) {
            if (ctx.getSessionId() == null) {
                // S0 前置失败：会话都还没建，没有可留痕的对象，直接按参数 / 归属错误返回
                throw ex;
            }
            ctx.setErrorCode(ex.getCode());
            ctx.setErrorMsg(ex.getMessage());
            ctx.setAnswer(buildErrorAnswer(ctx));
            return finish(ctx, resp, STATE_FAILED);
        } catch (ModelException ex) {
            // CONFIG_ERROR = 场景未绑定模型 / 绑定的模型不可用：属「配置未就绪」，按 DEGRADED 提示去配置，
            // 与真正的调用失败（限流耗尽、超时、熔断）区分开，前端给的处置动作不一样
            if (ex.getCategory() == ModelErrorCategory.CONFIG_ERROR) {
                ctx.setErrorCode(KNOWLEDGE_CHAT_MODEL_NOT_READY.getCode());
                ctx.setErrorMsg(KNOWLEDGE_CHAT_MODEL_NOT_READY.getMsg());
                ctx.setAnswer(buildModelNotReadyAnswer(ctx));
                return finish(ctx, resp, STATE_DEGRADED);
            }
            ctx.setErrorCode(KNOWLEDGE_CHAT_MODEL_CALL_FAILED.getCode());
            ctx.setErrorMsg("模型调用失败[" + ex.getCategory() + "]：" + safeMessage(ex));
            ctx.setAnswer(buildErrorAnswer(ctx));
            return finish(ctx, resp, STATE_FAILED);
        } catch (Exception ex) {
            log.error("[knowledge-chat][unexpected] traceId={}, baseId={}, sessionId={}",
                    ctx.getTraceId(), ctx.getBaseId(), ctx.getSessionId(), ex);
            ctx.setErrorMsg("知识库问答链路异常：" + safeMessage(ex));
            ctx.setAnswer(buildErrorAnswer(ctx));
            return finish(ctx, resp, STATE_FAILED);
        }
    }

    // ==================== S0 建立上下文 ====================

    private void s0Context(KnowledgeChatContext ctx, AiKnowledgeChatAskReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            // 未登录由安全框架在上层拦截，这里做纵深防御
            throw exception(TENANT_CONTEXT_MISSING);
        }
        String question = reqVO.getQuestion() == null ? "" : reqVO.getQuestion().trim();
        if (!StringUtils.hasText(question)) {
            throw exception(AI_CHAT_QUESTION_EMPTY);
        }
        int maxLen = properties.getMaxQuestionLength() == null ? 500 : properties.getMaxQuestionLength();
        if (question.length() > maxLen) {
            throw exception(AI_CHAT_QUESTION_TOO_LONG);
        }
        if (reqVO.getBaseId() == null) {
            throw exception(KNOWLEDGE_CHAT_BASE_ID_REQUIRED);
        }

        ctx.setTenantId(tenantId);
        ctx.setUserId(userId);
        ctx.setBaseId(reqVO.getBaseId());
        ctx.setQuestion(question);
        ctx.setSceneCode(SCENE_RAG);
        ctx.setPreferredModelConfigId(reqVO.getModelConfigId());
        ctx.setTraceId(UUID.randomUUID().toString().replace("-", ""));

        // 知识库归属 + 启用校验：跨租户 / 已删除一律按「不存在」处理
        AiKnowledgeBase base = knowledgeBaseService.getKnowledgeBase(reqVO.getBaseId());
        if (base == null) {
            throw exception(KNOWLEDGE_BASE_NOT_EXISTS);
        }
        if (base.getStatus() != null && base.getStatus() != 0) {
            throw exception(KNOWLEDGE_CHAT_BASE_DISABLED);
        }
        ctx.setBaseName(base.getName());
        ctx.setNormalizedQuestion(normalize(question));

        // 会话：显式传入则复用（校验归属 + 不可换库），否则新建
        if (reqVO.getSessionId() != null) {
            AiKnowledgeSession session = conversationService.requireSession(reqVO.getSessionId());
            if (!base.getId().equals(session.getBaseId())) {
                throw exception(KNOWLEDGE_CHAT_SESSION_BASE_MISMATCH);
            }
            ctx.setSessionId(session.getId());
            ctx.setSessionNo(session.getSessionNo());
        } else {
            AiKnowledgeSession created = sessionService.createSession(base.getId(), question, ctx.getTraceId());
            ctx.setSessionId(created.getId());
            ctx.setSessionNo(created.getSessionNo());
        }
    }

    /** 提示词注入 / 越权特征检测（保守命中，宁可拒绝也不硬答） */
    private static boolean isInjection(String question) {
        // 配置开关由调用方判定，这里只做纯规则匹配，便于单测
        String lower = question.toLowerCase(Locale.ROOT);
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ==================== S3 提示词 ====================

    /**
     * RAG 提示词渲染：context / history 由服务端 set（VO 上 @JsonIgnore，前端无法伪造）。
     * datasourceId 不传：知识库问答不需要方言与 SQL 规则变量。
     */
    private PromptRenderRespVO renderPrompt(KnowledgeChatContext ctx) {
        PromptRenderReqVO reqVO = new PromptRenderReqVO();
        reqVO.setSceneCode(SCENE_RAG);
        reqVO.setQuestion(ctx.getNormalizedQuestion() == null ? ctx.getQuestion() : ctx.getNormalizedQuestion());
        reqVO.setRagContext(ctx.getRecallResult() == null ? "" : ctx.getRecallResult().getContextText());
        reqVO.setRagHistory(ctx.getHistoryText() == null ? "" : ctx.getHistoryText());
        reqVO.setRecallSchema(false);
        return promptRenderService.render(reqVO);
    }

    // ==================== S4 模型调用 ====================

    private ModelInvocationResult invokeModel(KnowledgeChatContext ctx, PromptRenderRespVO prompt,
                                              StreamingCitationGate gate) {
        ModelInvocation invocation = new ModelInvocation();
        invocation.setTraceId(ctx.getTraceId());
        invocation.setSceneCode(SCENE_RAG);
        invocation.setPrompt(prompt.getContent());
        invocation.setConversationId(ctx.getSessionId());
        invocation.setUserId(ctx.getUserId());
        invocation.setTenantId(ctx.getTenantId());
        invocation.setSessionId(ctx.getSessionNo() != null ? ctx.getSessionNo() : String.valueOf(ctx.getSessionId()));
        invocation.setPromptId(prompt.getPromptId());
        invocation.setTimeoutMs(properties.getModelTimeoutMs());
        // 对话页「选择模型」偏好：只在 RAG 场景生效，且由路由器校验场景候选集
        invocation.setPreferredModelConfigId(ctx.getPreferredModelConfigId());
        invocation.setCancelToken(ctx.getCancelToken());
        if (gate != null) {
            // 流式不用 model-timeout-ms 一刀切：长回答会被说到一半硬砍。改两段式（首 token / 增量间隔）
            invocation.setStreamFirstTokenMs(properties.getStreamFirstTokenMs());
            invocation.setStreamIdleMs(properties.getStreamIdleMs());
            ctx.setStreaming(true);
            return modelGovernanceExecutor.executeStream(invocation, streamModelClient, delta -> {
                // TTFT 必须记在门控之前：门控为了校验引用会 holdback 最多 6 个字符，
                // 放在后面会让首字耗时被放大且随文本内容抖动
                ctx.markFirstToken();
                boolean alreadyTruncated = gate.isTruncated();
                String allowed = gate.feed(delta);
                emitDelta(ctx, allowed);
                if (!alreadyTruncated && gate.isTruncated()) {
                    // 字数上限到了：把最后允许的那一段发完后叫停供应商。
                    // 主动控制流不能当故障——治理层与客户端都识别这个信号，按成功收口。
                    throw new StreamAbortSignal("answer chars limit reached");
                }
            });
        }
        return modelGovernanceExecutor.execute(invocation, modelClient);
    }

    // ==================== S6 收尾 ====================

    private AiKnowledgeChatRespVO finish(KnowledgeChatContext ctx, AiKnowledgeChatRespVO resp, String state) {
        ctx.setTerminalState(state);
        resp.setSessionId(ctx.getSessionId());
        resp.setSessionNo(ctx.getSessionNo());
        resp.setTraceId(ctx.getTraceId());
        resp.setBaseId(ctx.getBaseId());
        resp.setBaseName(ctx.getBaseName());
        resp.setQuestion(ctx.getQuestion());
        resp.setStatus(state);
        resp.setAnswer(ctx.getAnswer());
        // references 始终给列表（空而不是 null）：前端直接 map 渲染，不必为澄清/拒绝分支判空
        resp.setReferences(ctx.getCitation() == null ? List.of() : ctx.getCitation().getReferences());
        resp.setRecallMode(ctx.getRecallResult() == null ? null : ctx.getRecallResult().getMode());
        resp.setRecallDegraded(ctx.isRecallDegraded());
        resp.setRecallDegradeReason(ctx.getRecallResult() == null ? null : ctx.getRecallResult().getDegradeReason());
        resp.setRetrievedCount(ctx.retrievedCount());
        resp.setCitationDroppedCount(ctx.getCitationDroppedCount());
        resp.setPromptVersionId(ctx.getPromptVersionId());
        resp.setModelId(ctx.getModelId());
        resp.setModelConfigId(ctx.getModelConfigId());
        resp.setErrorCode(ctx.getErrorCode());
        resp.setErrorMsg(ctx.getErrorMsg());
        resp.setDegradeReason(ctx.getDegradeReason());
        resp.setLatencyMs(ctx.elapsedMs());

        // 对话树落库：失败只记日志，不影响问答终态（异常在 Writer 内已回滚整笔）
        try {
            Long assistantId = messageTreeWriter.write(ctx);
            resp.setUserMessageId(ctx.getUserMessageId());
            resp.setAssistantMessageId(assistantId);
        } catch (Exception ex) {
            log.warn("[knowledge-chat][message-record] 落库失败 traceId={}, sessionId={}, reason={}",
                    ctx.getTraceId(), ctx.getSessionId(), safeMessage(ex));
        }

        // 终态事件：done/error/stopped 的 payload 带完整统一结果，与 /ask 同口径
        if (STATE_STOPPED.equals(state)) {
            emit(ctx, "stopped", ChatStageEvent.STOPPED, "已停止生成", 100,
                    e -> enrichTerminal(e, ctx, resp)
                            .put("stopReason", ctx.getStopReason()));
        } else if (STATE_DONE.equals(state)) {
            emit(ctx, "done", ChatStageEvent.DONE, "问答完成", 100,
                    e -> enrichTerminal(e, ctx, resp)
                            .put("references", resp.getReferences()));
        } else {
            emit(ctx, "error", ChatStageEvent.ERROR,
                    resp.getErrorMsg() == null ? "问答未成功：" + state : resp.getErrorMsg(), 100,
                    e -> enrichTerminal(e, ctx, resp)
                            .put("errorCode", resp.getErrorCode()));
        }
        logStreamSummary(ctx, state);
        return resp;
    }

    /**
     * 三个终态共有的 payload。persisted=false 表示本轮按版本策略没有落库：
     * 前端据此把重跑气泡回滚到旧版本（edit 还要回滚问题气泡），否则用户刷新后会以为编辑丢了。
     */
    private ChatStageEvent enrichTerminal(ChatStageEvent e, KnowledgeChatContext ctx, AiKnowledgeChatRespVO resp) {
        return e.put("status", resp.getStatus())
                .put("sessionId", resp.getSessionId())
                .put("answer", resp.getAnswer())
                .put("op", ctx.getMessageOp())
                .put("assistantMessageId", resp.getAssistantMessageId())
                .put("userMessageId", resp.getUserMessageId())
                .put("version", ctx.getAssistantVersion())
                .put("persisted", resp.getAssistantMessageId() != null)
                .put("metrics", streamMetrics(ctx));
    }

    /**
     * 流式观测：TTFT 只进事件与日志，不落库（TTFT 是运维指标，不是业务字段）。
     * 一行一条便于日志平台直接切 P50 / P90；同步链路（未流式）不输出。
     */
    private void logStreamSummary(KnowledgeChatContext ctx, String state) {
        if (!ctx.isStreaming()) {
            return;
        }
        log.info("[knowledge-chat][stream] traceId={} modelId={} state={} stopReason={} ttft={}ms total={}ms deltas={} chars={}",
                ctx.getTraceId(), ctx.getModelId(), state,
                ctx.getStopReason() == null ? "NONE" : ctx.getStopReason(),
                ctx.ttftMs(), ctx.elapsedMs(), ctx.deltaCount(), ctx.getPushedChars());
    }

    private Map<String, Object> streamMetrics(KnowledgeChatContext ctx) {
        if (!ctx.isStreaming()) {
            return null;
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        int ttft = ctx.ttftMs();
        if (ttft >= 0) {
            metrics.put("ttftMs", ttft);
        }
        metrics.put("deltaCount", ctx.deltaCount());
        metrics.put("pushedChars", ctx.getPushedChars());
        metrics.put("totalMs", ctx.elapsedMs());
        return metrics;
    }

    /** 取消归因：先到者定因（令牌 first-wins），缺失时按用户停止处理。 */
    private static String resolveStopReason(KnowledgeChatContext ctx) {
        ChatCancelToken token = ctx.getCancelToken();
        ChatCancelToken.Reason reason = token == null ? null : token.getCancelReason();
        if (reason == ChatCancelToken.Reason.CLIENT_GONE) {
            return "CLIENT_GONE";
        }
        if (reason == ChatCancelToken.Reason.TIMEOUT) {
            return "TIMEOUT";
        }
        return "USER_CANCEL";
    }

    private static String stopMessage(String reason) {
        if ("CLIENT_GONE".equals(reason)) {
            return "生成已中断（客户端断开）";
        }
        if ("TIMEOUT".equals(reason)) {
            return "生成已超时中断";
        }
        return "用户已停止生成";
    }

    /**
     * 阶段事件发送：sink 为 null（同步 /ask）时零开销；发送异常一律吞掉——
     * 客户端断流不能中断已经开始的检索、模型调用与落库。
     */
    private void emit(KnowledgeChatContext ctx, String stage, String state, String message, int progress) {
        emit(ctx, stage, state, message, progress, null);
    }

    private void emit(KnowledgeChatContext ctx, String stage, String state, String message,
                      int progress, java.util.function.Consumer<ChatStageEvent> enricher) {
        ChatStageSink sink = ctx.getStageSink();
        if (sink == null) {
            return;
        }
        try {
            ChatStageEvent event = ChatStageEvent.of(stage, state, message, progress);
            event.setTraceId(ctx.getTraceId());
            event.setLatencyMs((long) ctx.elapsedMs());
            if (enricher != null) {
                enricher.accept(event);
            }
            sink.emit(event);
        } catch (Exception ignored) {
            // 断流不影响业务链路
        }
    }

    /**
     * 增量下发：sink 为 null 或内容为空时零开销；发送异常一律吞掉——
     * 断流不影响已经生成的文本与后续落库（会话记录仍要留痕）。
     */
    private void emitDelta(KnowledgeChatContext ctx, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        ChatStageSink sink = ctx.getStageSink();
        if (sink == null) {
            return;
        }
        try {
            ChatDeltaEvent event = new ChatDeltaEvent();
            event.setContent(content);
            event.setSeq(ctx.nextDeltaSeq());
            event.setTraceId(ctx.getTraceId());
            sink.delta(event);
            // 只统计真正发出去的字符：断连后不再累加，保证 chars 与前端所见口径一致
            ctx.setPushedChars(ctx.getPushedChars() + content.length());
        } catch (Exception ignored) {
            // 客户端已断流：不中断本轮生成，落库仍按已生成文本收口
        }
    }

    // ==================== 话术 ====================

    private String buildClarifyAnswer(KnowledgeChatContext ctx) {
        String name = StringUtils.hasText(ctx.getBaseName()) ? "《" + ctx.getBaseName() + "》" : "该知识库";
        return "未在" + name + "中检索到与问题相关的内容。可以尝试：\n"
                + "1. 换用更具体或更贴近文档原文的说法；\n"
                + "2. 确认相关文档已上传，且解析状态为「已完成」（切片生成后才会参与检索）；\n"
                + "3. 或切换到包含相关资料的其他知识库后再提问。";
    }

    private String buildModelNotReadyAnswer(KnowledgeChatContext ctx) {
        int count = ctx.retrievedCount() == null ? 0 : ctx.retrievedCount();
        return "已在该知识库检索到 " + count + " 条相关资料，但当前未接入可用的对话模型，"
                + "无法生成回答。请联系管理员在「模型配置」中为 RAG 场景绑定可用的对话模型。";
    }

    private String buildErrorAnswer(KnowledgeChatContext ctx) {
        if (StringUtils.hasText(ctx.getAnswer())) {
            return ctx.getAnswer();
        }
        if (StringUtils.hasText(ctx.getErrorMsg())) {
            return "本次知识库问答未能完成：" + ctx.getErrorMsg();
        }
        return "本次知识库问答未能完成，请稍后重试。";
    }

    // ==================== 工具 ====================

    private int maxAnswerChars() {
        return properties.getMaxAnswerChars() == null ? 8000 : properties.getMaxAnswerChars();
    }

    /** 规范化：折叠连续空白，仅用于召回与提示词输入，原文 question 保持不变 */
    private static String normalize(String question) {
        return question.replaceAll("\\s+", " ").trim();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.debug("[knowledge-chat][references] 序列化失败 reason={}", ex.getMessage());
            return null;
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String safeMessage(Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            return ex.getClass().getSimpleName();
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("jdbc:") || lower.contains("password") || lower.contains("api key")
                || lower.contains("authorization")) {
            return "调用或存储异常，详见服务端日志（凭证不回显）";
        }
        return clip(msg, 300);
    }
}
