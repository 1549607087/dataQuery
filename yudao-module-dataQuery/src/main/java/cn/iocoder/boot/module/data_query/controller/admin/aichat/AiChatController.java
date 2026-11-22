package cn.iocoder.boot.module.data_query.controller.admin.aichat;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatFeedbackReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatModelOptionRespVO;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiQueryAudit;
import cn.iocoder.boot.module.data_query.entity.AiQueryFeedback;
import cn.iocoder.boot.module.data_query.framework.chat.ChatOrchestratorProperties;
import cn.iocoder.boot.module.data_query.framework.chat.ChatSseExecutor;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageEvent;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.boot.module.data_query.framework.chat.QueryOrchestrator;
import cn.iocoder.boot.module.data_query.framework.model.ModelException;
import cn.iocoder.boot.module.data_query.framework.model.ModelRouter;
import cn.iocoder.boot.module.data_query.service.AiQueryAuditService;
import cn.iocoder.boot.module.data_query.service.AiQueryFeedbackService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_FEEDBACK_AUDIT_NOT_FOUND;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_FEEDBACK_NOT_OWNED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_SSE_BUSY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_SSE_DISABLED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 智能问数（P5 同步 + P6 流式与反馈）。
 *
 * 一次请求走完 S0 ~ S9：鉴权与归属 → 规范化 → 意图 → Schema 召回 → 提示词装配
 * → 受治理模型调用 → SQL 静态校验 → 只读执行 → 结果加工 → 审计回写。
 *
 * 安全边界全部在服务端：Schema 白名单由 S3 召回生成、S6/S7 强制走只读闸门、
 * 模型原始文本绝不直接进 JDBC。/ask-stream 复用同一个 QueryOrchestrator，
 * 只是额外接一个阶段事件输出口，业务链路完全不分叉。
 */
@Slf4j
@Tag(name = "管理后台 - 智能问数")
@RestController
@RequestMapping("/data-query/ai-chat")
@Validated
public class AiChatController {

    @Resource
    private QueryOrchestrator queryOrchestrator;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private ChatSseExecutor chatSseExecutor;

    @Resource
    private ChatOrchestratorProperties properties;

    @Resource
    private AiQueryAuditService auditService;

    @Resource
    private AiQueryFeedbackService feedbackService;

    @PostMapping("/ask")
    @Operation(summary = "智能问数：一次请求完成召回、生成、校验、只读执行与审计")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<AiChatAskRespVO> ask(@Valid @RequestBody AiChatAskReqVO reqVO) {
        return success(queryOrchestrator.ask(reqVO));
    }

    /**
     * P6 SSE 阶段流：事件名统一为 {@code stage}，data 为 ChatStageEvent JSON。
     *
     * 阶段顺序：intent → rewrite → recalled → generated → validated → executing
     *         → result → summary → done（失败时为 error）。
     * 只有 done/error 事件的 payload.response 带完整结果表格，其余阶段不含业务数据行。
     *
     * 注意：浏览器原生 EventSource 只支持 GET，本端点是 POST + JSON body，
     * 前端须用 fetch + ReadableStream（或 fetch-event-source 类库）消费。
     */
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "智能问数流式版：按阶段推送 SSE 事件，最终结果与 /ask 完全一致")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public SseEmitter askStream(@Valid @RequestBody AiChatAskReqVO reqVO) {
        long timeout = properties.getSseTimeoutMs() == null ? 70000L : properties.getSseTimeoutMs().longValue();
        SseEmitter emitter = new SseEmitter(timeout);
        ChatStageSink sink = event -> {
            try {
                emitter.send(SseEmitter.event().name("stage").data(event, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                // 客户端提前断开是正常行为：编排器继续跑完业务与审计，只是不再发事件
            }
        };

        if (!Boolean.TRUE.equals(properties.getSseEnabled())) {
            sink.emit(errorEvent(AI_CHAT_SSE_DISABLED.getMsg(), AI_CHAT_SSE_DISABLED.getCode()));
            emitter.complete();
            return emitter;
        }

        // 上下文必须在请求线程捕获：TenantContextHolder / SecurityContextHolder 不会跨线程传播
        Long tenantId = TenantContextHolder.getTenantId();
        boolean tenantIgnore = TenantContextHolder.isIgnore();
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (tenantId == null || loginUser == null) {
            sink.emit(errorEvent("缺少租户或登录上下文", AI_CHAT_SSE_DISABLED.getCode()));
            emitter.complete();
            return emitter;
        }

        try {
            chatSseExecutor.submit(tenantId, tenantIgnore, loginUser, () -> {
                try {
                    queryOrchestrator.ask(reqVO, sink);
                } catch (Exception ex) {
                    // 编排器内部已捕获全部业务异常；这里只兜底未预期的逃逸异常
                    sink.emit(errorEvent("问数链路异常，详见服务端日志", null));
                } finally {
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // 连接已关闭
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            sink.emit(errorEvent(AI_CHAT_SSE_BUSY.getMsg(), AI_CHAT_SSE_BUSY.getCode()));
            emitter.complete();
        }
        return emitter;
    }

    /**
     * P6 反馈闭环：服务端从登录态与 auditId 对应的审计记录固化归属，
     * 前端只能提交评分 / 类型 / 文字 / 纠正 SQL。correctedSql 只入库供人工审核，
     * 不执行、不自动改写线上提示词版本。
     */
    @PostMapping("/feedback")
    @Operation(summary = "对本次问数结果提交反馈（归属由服务端固化，需人工审核）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:feedback')")
    public CommonResult<Long> feedback(@Valid @RequestBody AiChatFeedbackReqVO reqVO) {
        AiQueryAudit audit = auditService.getAiQueryAudit(reqVO.getAuditId());
        if (audit == null) {
            // 跨租户记录会被 TenantDatabaseInterceptor 过滤，与「不存在」同一口径，避免泄漏
            throw exception(AI_CHAT_FEEDBACK_AUDIT_NOT_FOUND);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || !userId.equals(audit.getUserId())) {
            throw exception(AI_CHAT_FEEDBACK_NOT_OWNED);
        }

        AiQueryFeedback entity = new AiQueryFeedback();
        entity.setAuditId(audit.getId());
        entity.setSessionId(audit.getSessionId());
        entity.setUserId(userId);
        entity.setRating(reqVO.getRating());
        entity.setFeedbackType(reqVO.getFeedbackType());
        entity.setComment(reqVO.getComment());
        entity.setCorrectedSql(reqVO.getCorrectedSql());
        entity.setReviewed(0); // 0 待审；不自动采纳、不自动进 few-shot
        return success(feedbackService.createAiQueryFeedback(entity));
    }

    /**
     * 对话页「选择模型」下拉的数据源。
     *
     * 只返回 SQL_GEN 场景候选集（= 真实可路由的模型），首个即默认首选；
     * 场景未配置 / 候选集为空时返回空列表而不是报错：问数阶段会用明确错误码提示，
     * 不该让下拉加载失败把人挡在页面外。
     */
    @GetMapping("/model-options")
    @Operation(summary = "对话页可选模型：返回当前场景候选集，首个为默认路由")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<List<AiChatModelOptionRespVO>> modelOptions() {
        List<AiChatModelOptionRespVO> options = new ArrayList<>();
        List<AiModelConfig> candidates;
        try {
            candidates = modelRouter.listSceneCandidates(QueryOrchestrator.SCENE_SQL_GEN);
        } catch (ModelException ex) {
            log.info("[modelOptions][scene={}] 无法解析候选集，返回空列表：{}",
                    QueryOrchestrator.SCENE_SQL_GEN, ex.getMessage());
            return success(options);
        }
        for (int i = 0; i < candidates.size(); i++) {
            AiModelConfig config = candidates.get(i);
            AiChatModelOptionRespVO item = new AiChatModelOptionRespVO();
            item.setModelConfigId(config.getId());
            item.setName(config.getName());
            item.setModelId(config.getModelId());
            item.setModelType(config.getModelType());
            item.setSupportStream(config.getSupportStream());
            item.setDefaultModel(i == 0);
            options.add(item);
        }
        return success(options);
    }

    private static ChatStageEvent errorEvent(String message, Integer errorCode) {
        return ChatStageEvent.of("error", ChatStageEvent.ERROR, message, 100)
                .put("status", "FAILED")
                .put("errorCode", errorCode);
    }
}
