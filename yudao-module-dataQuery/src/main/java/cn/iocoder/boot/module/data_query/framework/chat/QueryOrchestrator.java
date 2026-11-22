package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiQueryAudit;
import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.framework.model.ModelClient;
import cn.iocoder.boot.module.data_query.framework.model.ModelException;
import cn.iocoder.boot.module.data_query.framework.model.ModelGovernanceExecutor;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocation;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocationResult;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryAuditContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import cn.iocoder.boot.module.data_query.service.AiQueryAuditService;
import cn.iocoder.boot.module.data_query.service.AiQuerySessionService;
import cn.iocoder.boot.module.data_query.service.PromptRenderService;
import cn.iocoder.boot.module.data_query.service.ReadOnlyQueryService;
import cn.iocoder.boot.module.data_query.service.SchemaRecallService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_DATASOURCE_DISABLED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_DATASOURCE_UNHEALTHY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_MODEL_CALL_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_MODEL_NOT_READY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_PROMPT_INJECTION;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_QUESTION_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_QUESTION_TOO_LONG;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_RECALL_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.QUERY_SESSION_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * P5 问数编排器：把 S0 ~ S9 串成一条服务端闭环。
 *
 * 安全红线（均为代码硬约束，无法用配置关闭）：
 * 1. 数据源归属：S0 用 AiDatasourceOwnerValidator 校验，跨租户一律按「不存在」处理。
 * 2. Schema 白名单：S3 只由服务端调用 SchemaRecallService 生成 SchemaContext，
 *    不接受前端注入 schema 文本 / 表名；S4 直接复用该 SchemaContext，S6 用它作白名单。
 * 3. 执行入口：S6/S7 只走 ReadOnlyQueryService#execute（内部强制先过 S6），
 *    模型原始文本绝不直接进 JDBC，也不另开绕过闸门的执行路径。
 * 4. 模型结果：ModelInvocationResult.echo=true（EchoModelClient 占位）绝不当作业务结果，
 *    直接降级为 DEGRADED 并如实说明「未接入真实模型」。
 * 5. 修正预算：SQL_FIX 与 S6/S7 共用同一个 maxFixRounds，用完即停，防止成本放大。
 *
 * 一期取舍（已在 docs/intelligent-query-flow.md 记录）：
 * - S2 意图识别先用规则，不调用 INTENT 模型（maxIntentRounds 预留给后续版本）。
 * - SQL_FIX 场景暂无模板与模型绑定，修正轮复用 SQL_GEN 提示词并追加「上一轮 SQL + 失败原因」，
 *   仍走 ModelGovernanceExecutor 与 S6/S7，不绕过任何闸门。
 * - S8 结果解读（RESULT_SUMMARY）默认关闭（resultSummaryEnabled=false），
 *   且 RESULT_SUMMARY 场景暂无绑定，开启后失败也只降级、不阻断主结果。
 */
@Slf4j
@Service
public class QueryOrchestrator {

    /** SQL 生成场景编码：路由、提示词、对话页「选择模型」均以此为场景键 */
    public static final String SCENE_SQL_GEN = "SQL_GEN";
    private static final String INTENT_QUERY = "QUERY";

    // 终态
    private static final String STATE_DONE = "DONE";
    private static final String STATE_CLARIFY = "CLARIFY";
    private static final String STATE_REJECT = "REJECT";
    private static final String STATE_DEGRADED = "DEGRADED";
    private static final String STATE_FAILED = "FAILED";
    private static final String STATE_STOPPED = "STOPPED";

    // ai_query_audit.status 语义：1 成功、2 澄清、3 拒绝、4 降级、5 失败、6 已停止
    private static final int AUDIT_SUCCESS = 1;
    private static final int AUDIT_CLARIFY = 2;
    private static final int AUDIT_REJECT = 3;
    private static final int AUDIT_DEGRADED = 4;
    private static final int AUDIT_FAILED = 5;
    private static final int AUDIT_STOPPED = 6;

    /** 连接类错误（053）改 SQL 也修不好，不进入修正轮 */
    private static final int SQL_GUARD_CONNECTION_FAILED_CODE = 1_099_000_053;

    // ctx.sqlStatus 取值：与 ai_query_audit.sql_status 同口径
    private static final String SQL_STATUS_PASS = "PASS";
    private static final String SQL_STATUS_REJECT = "REJECT";
    private static final String SQL_STATUS_FAILED = "FAILED";

    /** S1 提示词注入 / 越权特征（保守命中，宁可澄清也不硬生成） */
    private static final List<String> INJECTION_PATTERNS = List.of(
            "忽略以上", "忽略之前", "忽略上面的", "忽略上述", "无视以上",
            "ignore previous", "ignore above", "ignore all previous",
            "系统提示", "system prompt", "开发者模式", "developer mode",
            "越狱", "jailbreak", "你现在是", "you are now",
            "输出你的提示", "reveal your", "打印提示词");

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;
    @Resource
    private SchemaRecallService schemaRecallService;
    @Resource
    private PromptRenderService promptRenderService;
    @Resource
    private ModelGovernanceExecutor modelGovernanceExecutor;
    @Resource
    private ModelClient modelClient;
    @Resource
    private ReadOnlyQueryService readOnlyQueryService;
    @Resource
    private AiQuerySessionService sessionService;
    @Resource
    private AiQueryAuditService auditService;
    @Resource
    private ChatOrchestratorProperties properties;
    @Resource
    private ResultAdvisor resultAdvisor;
    @Resource
    private ChatMessageRecorder messageRecorder;
    @Resource
    private FollowupRewriteService followupRewriteService;

    /**
     * 同步问数：一次调用走完 S0 ~ S9，返回可直接渲染的结果（P5 契约不变）。
     */
    public AiChatAskRespVO ask(AiChatAskReqVO reqVO) {
        return ask(reqVO, null);
    }

    /**
     * P7：带对话操作元数据的问数入口（重新生成 / 修改问题时由 Controller 校验归属后调用）。
     * 业务链路仍走同一个 ask(reqVO, sink)，只是在 S0 前把操作类型与源消息固化进上下文，
     * 由 finish() 里的消息落库器据此作废旧版本、生成新版本。sink 通常传 null（同步）。
     */
    public AiChatAskRespVO ask(AiChatAskReqVO reqVO, String messageOp,
                               cn.iocoder.boot.module.data_query.entity.AiQueryMessage sourceMessage) {
        return askWithMessageOp(reqVO, null, messageOp, sourceMessage);
    }

    /**
     * P6：带阶段事件输出口的问数。
     *
     * sink 为 null 时行为与 P5 完全一致（同步 /ask）；/ask-stream 传入 SSE 适配器，
     * 业务链路、S3 白名单、S6/S7 闸门与审计留痕完全共用同一实现，不另开分叉。
     * 事件发送异常一律吞掉：客户端断流不能中断已经开始的问数与审计。
     */
    public AiChatAskRespVO ask(AiChatAskReqVO reqVO, ChatStageSink sink) {
        return askWithMessageOp(reqVO, sink, ChatMessageRecorder.OP_ASK, null);
    }

    /** 真正的 ask 实现：messageOp / sourceMessage 仅在 P7 重新生成 / 修改问题时非默认值 */
    private AiChatAskRespVO askWithMessageOp(AiChatAskReqVO reqVO, ChatStageSink sink,
                                             String messageOp,
                                             cn.iocoder.boot.module.data_query.entity.AiQueryMessage sourceMessage) {
        QueryContext ctx = new QueryContext();
        ctx.setStageSink(sink);
        // 流式 ask 在 Controller 里把取消令牌放入 ChatCancelContext；同步/regenerate/edit 为 null
        ctx.setCancelToken(ChatCancelContext.current());
        if (messageOp != null) {
            ctx.setMessageOp(messageOp);
        }
        if (sourceMessage != null) {
            ctx.setSourceMessageId(sourceMessage.getId());
            ctx.setSourceMessage(sourceMessage);
        }
        AiChatAskRespVO resp = new AiChatAskRespVO();
        try {
            // ==================== S0 建立上下文 ====================
            s0Context(ctx, reqVO);
            resp.setSessionId(ctx.getSessionId());
            resp.setTraceId(ctx.getTraceId());
            resp.setQuestion(ctx.getQuestion());
            emit(ctx, ChatStageEvent.of("intent", ChatStageEvent.RUNNING, "正在建立查询上下文", 5)
                    .put("datasourceId", ctx.getDatasourceId())
                    .put("question", ctx.getQuestion()));

            // ==================== S1 规范化 + 注入检测 + A 方案追问改写 ====================
            String normalized = s1Normalize(ctx);
            if (normalized == null) {
                emit(ctx, ChatStageEvent.of("rewrite", ChatStageEvent.ERROR, "提问命中注入检测，已拒绝", 100)
                        .put("errorCode", ctx.getErrorCode()));
                return finish(ctx, resp, STATE_REJECT, null);
            }
            // A 方案：会话内追问（如「那按月份呢」）结合上一轮问题补全成自包含问题，
            // 只影响 normalizedQuestion（S3 召回 / S4 提示词输入），原文 question 不变。
            // 注入检测已在上方基于原文完成，改写不会绕过安全闸门。
            FollowupRewriteService.RewriteResult rewritten = followupRewriteService.rewrite(
                    ctx.getSessionId(), normalized, resolveExcludeUserId(ctx));
            if (rewritten != null && rewritten.isApplied()) {
                ctx.setNormalizedQuestion(rewritten.getQuestion());
                ctx.setRewriteApplied(true);
                ctx.setRewriteSource(rewritten.getSource());
                ctx.setPrevQuestion(rewritten.getPrevQuestion());
            }
            resp.setNormalizedQuestion(ctx.getNormalizedQuestion());
            resp.setRewriteApplied(ctx.isRewriteApplied());
            resp.setRewriteSource(ctx.getRewriteSource());
            resp.setPrevQuestion(ctx.getPrevQuestion());
            emit(ctx, ChatStageEvent.of("rewrite", ChatStageEvent.DONE,
                            ctx.isRewriteApplied() ? "问题已结合会话历史补全" : "问题已规范化", 10)
                    .put("normalizedQuestion", ctx.getNormalizedQuestion())
                    .put("rewriteApplied", ctx.isRewriteApplied())
                    .put("rewriteSource", ctx.getRewriteSource())
                    .put("prevQuestion", ctx.getPrevQuestion())
                    .put("note", ctx.isRewriteApplied()
                            ? "追问已结合上一轮上下文补全后再召回（纯规则，未调用模型）"
                            : "当前问题为独立问法或无可参考历史，直接召回"));

            // ==================== S2 意图识别（规则） ====================
            ctx.setIntentCode(INTENT_QUERY);
            resp.setIntentCode(ctx.getIntentCode());
            emit(ctx, ChatStageEvent.of("intent", ChatStageEvent.DONE, "意图识别完成", 15)
                    .put("intentCode", INTENT_QUERY)
                    .put("strategy", "RULE"));

            // ==================== S3 Schema 召回 ====================
            emit(ctx, ChatStageEvent.of("recalled", ChatStageEvent.RUNNING, "正在召回相关表列", 20));
            SchemaContext schemaContext = schemaRecallService.recall(
                    ctx.getDatasourceId(), ctx.getNormalizedQuestion(), null);
            ctx.setSchemaContext(schemaContext);
            resp.setRecallMode(schemaContext.getRecallMode());
            resp.setRecallDegraded(schemaContext.getDegraded());
            resp.setRecallDegradeReason(schemaContext.getDegradeReason());
            resp.setRecallTableCount(schemaContext.getHitTableCount());
            resp.setStructVersion(schemaContext.getStructVersion());
            resp.setRelationExpandedTableIds(schemaContext.getRelationExpandedTableIds());
            emit(ctx, ChatStageEvent.of("recalled", ChatStageEvent.DONE, "Schema 召回完成", 40)
                    .put("recallMode", schemaContext.getRecallMode())
                    .put("tableCount", schemaContext.getHitTableCount())
                    .put("degraded", schemaContext.getDegraded())
                    .put("degradeReason", schemaContext.getDegradeReason())
                    .put("relationExpandedTableIds", schemaContext.getRelationExpandedTableIds()));
            if (schemaContext.getTables() == null || schemaContext.getTables().isEmpty()) {
                // 召回为空 → CLARIFY，不硬生成 SQL，也不把全库 Schema 塞给模型
                ctx.setErrorCode(AI_CHAT_RECALL_EMPTY.getCode());
                ctx.setErrorMsg(AI_CHAT_RECALL_EMPTY.getMsg());
                return finish(ctx, resp, STATE_CLARIFY, null);
            }

            // ==================== S4 提示词装配 ====================
            ctx.checkCancelled();
            PromptRenderRespVO prompt = renderPrompt(ctx.getDatasourceId(),
                    ctx.getNormalizedQuestion(), schemaContext.getSchemaText());
            ctx.setPromptVersionId(prompt.getPromptVersionId());
            ctx.setPromptContentHash(prompt.getContentHash());
            resp.setPromptVersionId(prompt.getPromptVersionId());
            resp.setPromptContentHash(prompt.getContentHash());
            emit(ctx, ChatStageEvent.of("generated", ChatStageEvent.RUNNING, "提示词已装配，正在调用模型", 45)
                    .put("promptVersionId", prompt.getPromptVersionId())
                    .put("contentHash", prompt.getContentHash()));

            // ==================== S5 受治理的模型调用 ====================
            ctx.checkCancelled();
            ModelInvocationResult modelResult = invokeModel(ctx, prompt.getContent(), prompt.getPromptId());
            if (Boolean.TRUE.equals(modelResult.getEcho())) {
                // 仍是 EchoModelClient：绝不把回声当业务结果
                ctx.setErrorCode(AI_CHAT_MODEL_NOT_READY.getCode());
                ctx.setErrorMsg(AI_CHAT_MODEL_NOT_READY.getMsg());
                resp.setModelId(modelResult.getModelId());
                resp.setModelConfigId(modelResult.getModelConfigId());
                emit(ctx, ChatStageEvent.of("generated", ChatStageEvent.SKIPPED, "未接入可用 Chat 模型", 100)
                        .put("errorCode", ctx.getErrorCode()));
                return finish(ctx, resp, STATE_DEGRADED, null);
            }
            ctx.setModelId(modelResult.getModelId());
            ctx.setModelConfigId(modelResult.getModelConfigId());
            resp.setModelId(modelResult.getModelId());
            resp.setModelConfigId(modelResult.getModelConfigId());
            String generatedSql = modelResult.getContent();
            ctx.setGeneratedSql(generatedSql);
            resp.setGeneratedSql(generatedSql);
            emit(ctx, ChatStageEvent.of("generated", ChatStageEvent.DONE, "SQL 生成完成", 60)
                    .put("modelId", ctx.getModelId())
                    .put("sql", generatedSql));

            // ==================== S6 + S7 校验与只读执行（带修正预算） ====================
            ctx.checkCancelled();
            QueryResult queryResult = validateAndExecute(ctx, prompt.getContent(), generatedSql);
            ctx.setQueryResult(queryResult);
            ctx.setExecutedSql(queryResult.getExecutedSql());
            ctx.setAuditId(queryResult.getAuditId());
            ctx.setSqlStatus(SQL_STATUS_PASS);
            // 修正轮成功后不能把首轮的失败原因带回前端，否则 DONE 结果会被渲染成错误
            ctx.setErrorCode(null);
            ctx.setErrorMsg(null);
            emit(ctx, ChatStageEvent.of("result", ChatStageEvent.DONE, "只读查询执行完成", 85)
                    .put("rowCount", queryResult.getRowCount())
                    .put("columnCount", queryResult.getColumnNames() == null ? 0 : queryResult.getColumnNames().size())
                    .put("truncated", queryResult.getTruncated())
                    .put("masked", queryResult.getMasked())
                    .put("auditId", queryResult.getAuditId())
                    .put("fixRounds", ctx.getFixRounds()));

            // ==================== S8 结果加工（规则图表 / 口径说明 / 可选模型摘要） ====================
            ctx.checkCancelled();
            s8Enhance(ctx, queryResult);

            // ==================== S9 审计 / 会话回写 ====================
            return finish(ctx, resp, STATE_DONE, queryResult);
        } catch (CancelledException ex) {
            // 用户主动停止：以 STOPPED 正常收口，已产生的部分结果照常落库留痕，不记为失败
            log.info("[ask][cancelled] traceId={}, clientRequestId={}", ctx.getTraceId(), ex.getClientRequestId());
            ctx.setErrorMsg("用户已停止生成");
            return finish(ctx, resp, STATE_STOPPED, null);
        } catch (ServiceException ex) {
            // 归属 / 校验 / 参数类业务异常：错误码与原因如实回传（不回显连接串），
            // 终态与 S7 已留痕的审计口径保持一致：S6 拒绝 → REJECT，其余 → FAILED
            ctx.setErrorCode(ex.getCode());
            ctx.setErrorMsg(ex.getMessage());
            String state = SQL_STATUS_REJECT.equals(ctx.getSqlStatus()) ? STATE_REJECT : STATE_FAILED;
            return finish(ctx, resp, state, null);
        } catch (ModelException ex) {
            // 治理链（限流 → 熔断 → 超时 → 重试 → 降级）已全部耗尽：给出可区分的分类与错误码，
            // 便于前端提示「稍后重试」还是「去改模型配置 / 凭证」，而不是笼统的链路异常
            ctx.setErrorCode(AI_CHAT_MODEL_CALL_FAILED.getCode());
            ctx.setErrorMsg("模型调用失败[" + ex.getCategory() + "]：" + safeMessage(ex));
            return finish(ctx, resp, STATE_FAILED, null);
        } catch (Exception ex) {
            log.error("[ask][unexpected] traceId={}, datasourceId={}", ctx.getTraceId(), ctx.getDatasourceId(), ex);
            ctx.setErrorMsg("问数链路异常：" + safeMessage(ex));
            return finish(ctx, resp, STATE_FAILED, null);
        }
    }

    // ==================== S0 建立上下文 ====================

    private void s0Context(QueryContext ctx, AiChatAskReqVO reqVO) {
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

        // 先落上下文（traceId / 租户 / 用户 / 数据源），再做归属与状态校验：
        // 这样即使被归属校验拒绝，终态审计里也能留下 traceId 与发起人，便于追溯越权尝试
        ctx.setTenantId(tenantId);
        ctx.setUserId(userId);
        ctx.setDatasourceId(reqVO.getDatasourceId());
        ctx.setQuestion(question);
        ctx.setSceneCode(StringUtils.hasText(reqVO.getSceneCode()) ? reqVO.getSceneCode() : SCENE_SQL_GEN);
        ctx.setPreferredModelConfigId(reqVO.getModelConfigId());
        ctx.setTraceId(UUID.randomUUID().toString().replace("-", ""));

        // 数据源归属校验：跨租户 / 停用 / 删除 一律按「不存在」处理
        AiDatasource ds = ownerValidator.requireOwned(reqVO.getDatasourceId());
        if (ds.getStatus() != null && ds.getStatus() != 0) {
            throw exception(AI_CHAT_DATASOURCE_DISABLED);
        }
        if (ds.getHealthStatus() != null && ds.getHealthStatus() == 2) {
            throw exception(AI_CHAT_DATASOURCE_UNHEALTHY);
        }


        // 会话：显式传入则复用（同时校验归属），否则新建
        if (reqVO.getSessionId() != null) {
            AiQuerySession session = sessionService.getAiQuerySession(reqVO.getSessionId());
            if (session == null) {
                throw exception(QUERY_SESSION_NOT_EXISTS);
            }
            ctx.setSessionId(session.getId());
            ctx.setSessionNo(session.getSessionNo());
        } else {
            AiQuerySession session = new AiQuerySession();
            session.setUserId(userId);
            session.setDatasourceId(reqVO.getDatasourceId());
            session.setTitle(abbreviate(question, 30));
            session.setSessionNo(nextSessionNo());
            session.setSceneCode(ctx.getSceneCode());
            session.setStatus(1);
            session.setMessageCount(0);
            session.setLastQuestion(question);
            session.setLastAskTime(LocalDateTime.now());
            session.setTraceId(ctx.getTraceId());
            ctx.setSessionId(sessionService.createAiQuerySession(session));
            ctx.setSessionNo(session.getSessionNo());
        }
    }

    // ==================== S1 规范化 ====================

    /** @return 规范化问题；命中注入特征返回 null（上层置 REJECT） */
    private String s1Normalize(QueryContext ctx) {
        String question = ctx.getQuestion();
        if (Boolean.TRUE.equals(properties.getPromptInjectionGuardEnabled())) {
            String lower = question.toLowerCase(Locale.ROOT);
            for (String pattern : INJECTION_PATTERNS) {
                if (lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                    ctx.setErrorCode(AI_CHAT_PROMPT_INJECTION.getCode());
                    ctx.setErrorMsg(AI_CHAT_PROMPT_INJECTION.getMsg());
                    return null;
                }
            }
        }
        // 一期改写规则：折叠连续空白，去除首尾；多轮改写留待 REWRITE 场景接入
        String normalized = question.replaceAll("\\s+", " ").trim();
        ctx.setNormalizedQuestion(normalized);
        return normalized;
    }

    // ==================== S4 提示词 ====================

    private PromptRenderRespVO renderPrompt(Long datasourceId, String question, String schemaText) {
        PromptRenderReqVO reqVO = new PromptRenderReqVO();
        reqVO.setSceneCode(SCENE_SQL_GEN);
        reqVO.setDatasourceId(datasourceId);
        reqVO.setQuestion(question);
        // 直接复用 S3 已召回的 Schema 文本，避免二次召回；同时也保证与 S6 白名单同源
        reqVO.setSchema(schemaText);
        reqVO.setRecallSchema(false);
        return promptRenderService.render(reqVO);
    }

    // ==================== S5 模型调用 ====================

    private ModelInvocationResult invokeModel(QueryContext ctx, String promptContent, Long promptId) {
        ModelInvocation invocation = new ModelInvocation();
        invocation.setTraceId(ctx.getTraceId());
        invocation.setSceneCode(SCENE_SQL_GEN);
        invocation.setPrompt(promptContent);
        invocation.setConversationId(ctx.getSessionId());
        invocation.setUserId(ctx.getUserId());
        invocation.setTenantId(ctx.getTenantId());
        invocation.setSessionId(ctx.getSessionNo() != null ? ctx.getSessionNo() : String.valueOf(ctx.getSessionId()));
        invocation.setTimeoutMs(properties.getModelTimeoutMs());
        invocation.setPromptId(promptId);
        // 对话页「选择模型」的偏好：只在 SQL_GEN 场景生效，且由路由器校验候选集
        invocation.setPreferredModelConfigId(ctx.getPreferredModelConfigId());
        invocation.setCancelToken(ctx.getCancelToken());
        return modelGovernanceExecutor.execute(invocation, modelClient);
    }

    // ==================== S6 + S7 校验与执行（修正预算） ====================

    private QueryResult validateAndExecute(QueryContext ctx, String basePrompt, String generatedSql) {
        int maxFix = properties.getMaxFixRounds() == null ? 2 : properties.getMaxFixRounds();
        String currentSql = generatedSql;
        String lastError = null;
        while (true) {
            QueryAuditContext auditCtx = new QueryAuditContext();
            auditCtx.setSessionId(ctx.getSessionId());
            auditCtx.setTraceId(ctx.getTraceId());
            auditCtx.setPromptVersionId(ctx.getPromptVersionId());
            auditCtx.setModelConfigId(ctx.getModelConfigId());
            auditCtx.setIntentCode(ctx.getIntentCode());
            auditCtx.setNormalizedQuestion(ctx.getNormalizedQuestion());
            try {
                // S6 + S7 一体：内部先过 S6 闸门，拿到 SafeSql 才打开只读连接
                ctx.setExecutionAttempted(true);
                // generatedSql / validatedSql 统一记「本轮真正送进 S6 的 SQL」，修正后也能如实反映
                ctx.setGeneratedSql(currentSql);
                ctx.setValidatedSql(currentSql);
                emit(ctx, ChatStageEvent.of("validated", ChatStageEvent.RUNNING, "正在做 S6 静态校验", 65)
                        .put("attempt", ctx.getFixRounds() + 1)
                        .put("sql", currentSql));
                QueryResult executed = readOnlyQueryService.execute(ctx.getDatasourceId(), currentSql,
                        ctx.getNormalizedQuestion(), ctx.getSchemaContext(), auditCtx);
                // S6 与 S7 在同一个 Service 方法内完成，无法拆出精确的中间时刻：
                // validated 表示「进入闸门」，executing 表示「闸门通过且只读执行完成」。
                emit(ctx, ChatStageEvent.of("executing", ChatStageEvent.DONE, "闸门通过，只读查询完成", 80)
                        .put("rowCount", executed.getRowCount())
                        .put("auditId", executed.getAuditId())
                        .put("readonlyConnection", executed.getReadonlyConnection()));
                return executed;
            } catch (ServiceException ex) {
                ctx.setErrorCode(ex.getCode());
                ctx.setErrorMsg(ex.getMessage());
                lastError = ex.getMessage();
                emit(ctx, ChatStageEvent.of("validated", ChatStageEvent.ERROR, "校验 / 执行未通过", 65)
                        .put("errorCode", ex.getCode())
                        .put("errorMsg", ex.getMessage())
                        .put("fixRounds", ctx.getFixRounds()));
                // 修复预算用尽 / 连接类错误改 SQL 也无效 → 终止修正
                if (ctx.getFixRounds() >= maxFix || ex.getCode() == SQL_GUARD_CONNECTION_FAILED_CODE) {
                    // S6 静态拒绝在 S7 侧已按 REJECT 留痕；连接/执行类失败留痕为 FAILED，这里保持同口径
                    ctx.setSqlStatus(ex.getCode() == SQL_GUARD_CONNECTION_FAILED_CODE
                            ? SQL_STATUS_FAILED : SQL_STATUS_REJECT);
                    throw ex;
                }
                // 进入修正轮前检查取消：用户已停止则不再发起修正模型调用
                ctx.checkCancelled();
                // 进入修正轮：复用 SQL_GEN 提示词 + 追加上一轮 SQL 与失败原因，重新走 S5
                ctx.setFixRounds(ctx.getFixRounds() + 1);
                String fixPrompt = buildFixPrompt(basePrompt, currentSql, lastError);
                ModelInvocationResult fixed = invokeModel(ctx, fixPrompt, ctx.getPromptVersionId());
                if (Boolean.TRUE.equals(fixed.getEcho())) {
                    ctx.setSqlStatus(SQL_STATUS_REJECT);
                    throw ex;
                }
                currentSql = fixed.getContent();
                ctx.setModelId(fixed.getModelId());
                ctx.setModelConfigId(fixed.getModelConfigId());
                log.info("[ask][SQL_FIX] traceId={}, fixRound={}, error={}",
                        ctx.getTraceId(), ctx.getFixRounds(), abbreviate(lastError, 120));
            }
        }
    }

    private String buildFixPrompt(String basePrompt, String previousSql, String errorMsg) {
        return basePrompt
                + "\n\n【上一轮生成的 SQL】\n```sql\n" + (previousSql == null ? "" : previousSql) + "\n```"
                + "\n\n【校验 / 执行失败原因】\n" + (errorMsg == null ? "" : errorMsg)
                + "\n\n请仅输出修正后的单条 SELECT 语句（使用 markdown ```sql 代码块），"
                + "必须继续遵守上述全部约束，只使用给定表列，并带 LIMIT。不要输出任何解释。";
    }

    // ==================== S8 结果解读（可降级） ====================

    private String summarize(QueryContext ctx, QueryResult queryResult) {
        try {
            ModelInvocation invocation = new ModelInvocation();
            invocation.setTraceId(ctx.getTraceId());
            invocation.setSceneCode("RESULT_SUMMARY");
            invocation.setPrompt("请用一句话概括以下查询结果（不要编造数据）：问题=" + ctx.getNormalizedQuestion()
                    + "；返回行数=" + queryResult.getRowCount()
                    + "；列=" + queryResult.getColumnNames());
            invocation.setConversationId(ctx.getSessionId());
            invocation.setUserId(ctx.getUserId());
            invocation.setTenantId(ctx.getTenantId());
            invocation.setTimeoutMs(properties.getSummaryTimeoutMs());
            invocation.setCancelToken(ctx.getCancelToken());
            ModelInvocationResult result = modelGovernanceExecutor.execute(invocation, modelClient);
            return Boolean.TRUE.equals(result.getEcho()) ? null : result.getContent();
        } catch (Exception ex) {
            // 结果解读失败不阻断主结果
            log.info("[ask][summary] 降级 traceId={}, reason={}", ctx.getTraceId(), safeMessage(ex));
            return null;
        }
    }

    // ==================== S9 收尾 ====================

    private AiChatAskRespVO finish(QueryContext ctx, AiChatAskRespVO resp, String state, QueryResult queryResult) {
        ctx.setTerminalState(state);
        resp.setStatus(state);
        // S0 之前的校验失败也要能定位到请求：traceId / 问题原文由 ctx 回填
        resp.setTraceId(ctx.getTraceId());
        resp.setQuestion(ctx.getQuestion());
        resp.setResult(queryResult);
        resp.setSql(ctx.getExecutedSql() != null ? ctx.getExecutedSql()
                : (ctx.getValidatedSql() != null ? ctx.getValidatedSql() : ctx.getGeneratedSql()));
        resp.setGeneratedSql(ctx.getGeneratedSql());
        resp.setExecutedSql(ctx.getExecutedSql());
        resp.setSummary(ctx.getSummary());
        resp.setChartSpec(ctx.getChartSpec());
        resp.setExplain(ctx.getExplain());
        resp.setDegradeReason(ctx.getDegradeReason());
        resp.setErrorCode(ctx.getErrorCode());
        resp.setErrorMsg(ctx.getErrorMsg());
        resp.setFixRounds(ctx.getFixRounds());
        resp.setLatencyMs(ctx.elapsedMs());
        resp.setMessageId((ctx.getSessionId() == null ? "" : ctx.getSessionId())
                + ":" + (ctx.getTraceId() == null ? "" : ctx.getTraceId()));

        // 非 S7 而产生的终态（CLARIFY / REJECT / DEGRADED / 模型前置失败）需要编排器补审计；
        // 一旦进入过 S7（executionAttempted），S7 已为 REJECT / FAILED / SUCCESS 写过审计，
        // 这里不再重复写，避免一次问数产生两条审计。
        if (ctx.getAuditId() == null && !ctx.isExecutionAttempted()) {
            writeTerminalAudit(ctx, state);
        }
        updateSession(ctx);

        // P7：S0 已建立会话上下文时落一条问答消息（USER + ASSISTANT）。
        // 落库失败只记日志，不影响 ask 终态；消息 id 回填响应供重新生成 / 赞踩引用。
        Long assistantMessageId = messageRecorder.record(ctx, resp);
        if (assistantMessageId != null) {
            resp.setUserMessageId(ctx.getUserMessageId());
            resp.setAssistantMessageId(assistantMessageId);
        }

        // P6：终态事件在审计 / 会话回写之后发，保证前端收到 done 时链路已完整落账。
        // done 事件的 response 与 /ask 响应完全同口径，是唯一包含结果表格的事件。
        // STOPPED 单独发 stopped，避免前端把「已停止」当成成功结果渲染出表格
        String stage = STATE_STOPPED.equals(state) ? "stopped"
                : (STATE_FAILED.equals(state) ? "error" : "done");
        emit(ctx, ChatStageEvent.of(stage, state, "问数结束：" + state, 100)
                .put("status", state)
                .put("sessionId", resp.getSessionId())
                .put("messageId", resp.getMessageId())
                .put("errorCode", ctx.getErrorCode())
                .put("errorMsg", ctx.getErrorMsg())
                .put("fixRounds", ctx.getFixRounds())
                .put("response", resp));
        return resp;
    }

    /**
     * S8 结果加工：规则图表推荐 + 口径说明 + 可选模型摘要。
     * 三项彼此独立，任一失败只降级该增强项，绝不改变终态或主结果。
     */
    private void s8Enhance(QueryContext ctx, QueryResult queryResult) {
        boolean chart = false;
        boolean explain = false;
        boolean summary = false;
        if (Boolean.TRUE.equals(properties.getChartSuggestionEnabled())) {
            try {
                ctx.setChartSpec(resultAdvisor.recommend(queryResult, ctx.getNormalizedQuestion()));
                chart = ctx.getChartSpec() != null;
            } catch (Exception ex) {
                log.info("[ask][chart] 降级 traceId={}, reason={}", ctx.getTraceId(), safeMessage(ex));
            }
        }
        if (Boolean.TRUE.equals(properties.getResultExplainEnabled())) {
            try {
                ctx.setExplain(resultAdvisor.explain(queryResult, ctx.getSchemaContext(),
                        ctx.getNormalizedQuestion()));
                explain = StringUtils.hasText(ctx.getExplain());
            } catch (Exception ex) {
                log.info("[ask][explain] 降级 traceId={}, reason={}", ctx.getTraceId(), safeMessage(ex));
            }
        }
        if (Boolean.TRUE.equals(properties.getResultSummaryEnabled())) {
            try {
                ctx.setSummary(summarize(ctx, queryResult));
                summary = StringUtils.hasText(ctx.getSummary());
            } catch (Exception ex) {
                log.info("[ask][summary] 降级 traceId={}, reason={}", ctx.getTraceId(), safeMessage(ex));
            }
        }
        boolean any = chart || explain || summary;
        emit(ctx, ChatStageEvent.of("summary", any ? ChatStageEvent.DONE : ChatStageEvent.SKIPPED,
                any ? "结果加工完成" : "结果加工未启用或已降级", 95)
                .put("chartType", ctx.getChartSpec() == null ? null : ctx.getChartSpec().getType())
                .put("chartGenerated", chart)
                .put("explainGenerated", explain)
                .put("summaryGenerated", summary));
    }

    /**
     * 发出阶段事件。sink 为 null 时直接返回；发送异常一律吞掉，
     * 客户端断流不能中断已开始的问数与 ai_query_audit 留痕。
     */
    private static void emit(QueryContext ctx, ChatStageEvent event) {
        ChatStageSink sink = ctx.getStageSink();
        if (sink == null) {
            return;
        }
        event.setTraceId(ctx.getTraceId());
        event.setLatencyMs((long) ctx.elapsedMs());
        try {
            sink.emit(event);
        } catch (Exception ex) {
            // 故意不打 error：SSE 客户端提前关闭是正常行为，链路继续执行
            log.debug("[ask][stage] 事件发送失败 stage={}, reason={}", event.getStage(), safeMessage(ex));
        }
    }

    private void writeTerminalAudit(QueryContext ctx, String state) {
        try {
            AiQueryAudit audit = new AiQueryAudit();
            audit.setSessionId(ctx.getSessionId() == null ? 0L : ctx.getSessionId());
            audit.setUserId(ctx.getUserId());
            audit.setDatasourceId(ctx.getDatasourceId());
            audit.setTraceId(ctx.getTraceId());
            audit.setQuestion(ctx.getQuestion());
            audit.setNormalizedQuestion(ctx.getNormalizedQuestion());
            audit.setIntentCode(ctx.getIntentCode());
            audit.setPromptVersionId(ctx.getPromptVersionId());
            audit.setModelConfigId(ctx.getModelConfigId());
            audit.setGeneratedSql(ctx.getGeneratedSql());
            audit.setValidatedSql(ctx.getValidatedSql());
            audit.setExecutedSql(ctx.getExecutedSql());
            audit.setSqlStatus(ctx.getSqlStatus());
            audit.setResultRows(0L);
            audit.setResultTruncated(0);
            audit.setLatencyMs(ctx.elapsedMs());
            audit.setStatus(auditStatusOf(state));
            audit.setErrorCode(ctx.getErrorCode() == null ? null : String.valueOf(ctx.getErrorCode()));
            audit.setErrorMsg(clip(ctx.getErrorMsg(), 1000));
            audit.setReadonlyConnection(0);
            ctx.setAuditId(auditService.createAiQueryAudit(audit));
        } catch (Exception ex) {
            log.warn("[ask][audit] 终态审计写入失败 traceId={}, reason={}", ctx.getTraceId(), safeMessage(ex));
        }
    }

    private static int auditStatusOf(String state) {
        return switch (state) {
            case STATE_DONE -> AUDIT_SUCCESS;
            case STATE_CLARIFY -> AUDIT_CLARIFY;
            case STATE_REJECT -> AUDIT_REJECT;
            case STATE_DEGRADED -> AUDIT_DEGRADED;
            case STATE_STOPPED -> AUDIT_STOPPED;
            default -> AUDIT_FAILED;
        };
    }

    private void updateSession(QueryContext ctx) {
        try {
            AiQuerySession session = sessionService.getAiQuerySession(ctx.getSessionId());
            if (session == null) {
                return;
            }
            // 树模型：message_count 只统计「对话轮次」（每轮 USER + ASSISTANT = 2 条）。
            // ASK 新增一轮 +2；REGENERATE / EDIT 只是同一轮的兄弟版本，不新增轮次，不累加。
            String op = ctx.getMessageOp();
            if (op == null || ChatMessageRecorder.OP_ASK.equals(op)) {
                int count = session.getMessageCount() == null ? 0 : session.getMessageCount();
                session.setMessageCount(count + 2); // 用户提问 + 助手回答
            }
            session.setLastQuestion(ctx.getQuestion());
            session.setLastAskTime(LocalDateTime.now());
            session.setTraceId(ctx.getTraceId());
            if (!StringUtils.hasText(session.getTitle())) {
                session.setTitle(abbreviate(ctx.getQuestion(), 30));
            }
            sessionService.updateAiQuerySession(session);
        } catch (Exception ex) {
            log.warn("[ask][session] 会话回写失败 sessionId={}, reason={}", ctx.getSessionId(), safeMessage(ex));
        }
    }

    // ==================== 工具 ====================

    /**
     * A 方案追问改写：计算要从「上一轮上下文」中排除的 USER 消息 id。
     * - ASK：当前轮尚未落库，无历史可排除，返回 null（主链最后一条 USER 即上一轮）；
     * - REGENERATE：源消息是旧 ASSISTANT，其 parentId 即当前轮 USER，排除它避免自我拼接；
     * - EDIT：源消息就是旧 USER，排除它自身。
     */
    private static Long resolveExcludeUserId(QueryContext ctx) {
        if (ctx.getSourceMessage() == null) {
            return null;
        }
        cn.iocoder.boot.module.data_query.entity.AiQueryMessage src = ctx.getSourceMessage();
        if (ChatMessageRecorder.OP_EDIT.equals(ctx.getMessageOp())) {
            return src.getId();
        }
        if (ChatMessageRecorder.OP_REGENERATE.equals(ctx.getMessageOp())) {
            return src.getParentId();
        }
        return null;
    }

    private static String nextSessionNo() {
        String ts = String.valueOf(System.currentTimeMillis());
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return "QS" + ts + rand;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
        if (lower.contains("jdbc:") || lower.contains("password") || lower.contains("connection to ")) {
            return "连接或执行异常，详见服务端日志（连接串与凭证不回显）";
        }
        return clip(msg, 300);
    }
}
