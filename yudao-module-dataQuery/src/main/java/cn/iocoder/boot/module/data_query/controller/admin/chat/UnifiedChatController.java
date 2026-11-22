package cn.iocoder.boot.module.data_query.controller.admin.chat;

import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.ChatOperateReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.RetrievePreviewVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatMessageRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedSessionPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedSessionRespVO;
import cn.iocoder.boot.module.data_query.enums.ChatModeEnum;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelContext;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelRegistry;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelToken;
import cn.iocoder.boot.module.data_query.framework.chat.ChatOrchestratorProperties;
import cn.iocoder.boot.module.data_query.framework.chat.ChatSseExecutor;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageEvent;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStreamConnection;
import cn.iocoder.boot.module.data_query.framework.chat.SseHeartbeat;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeRecallResult;
import cn.iocoder.boot.module.data_query.service.KnowledgeRecallService;
import cn.iocoder.boot.module.data_query.service.UnifiedChatService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_SSE_BUSY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_SSE_DISABLED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_CANCEL_NOT_FOUND;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 统一对话入口。
 *
 * <p>前端只对接本控制器：mode=QUERY 走智能问数引擎，mode=KNOWLEDGE 走知识库问答引擎。
 * 引擎与数据表保持双轨，本层只做路由与统一 VO 转换。旧的 /ai-chat 与 /knowledge-chat
 * 控制器保留一个过渡期，前端迁移完成后再下线。
 *
 * <p>权限统一用 {@code data-query:chat:query}，内部按 mode 做资源归属校验（下沉到各引擎）。
 */
@Slf4j
@Tag(name = "管理后台 - 统一对话")
@RestController
@RequestMapping("/data-query/chat")
@Validated
public class UnifiedChatController {

    @Resource
    private UnifiedChatService chatService;

    @Resource
    private KnowledgeRecallService recallService;

    @Resource
    private KnowledgeProperties knowledgeProperties;
    @Resource
    private ChatOrchestratorProperties chatProperties;

    @Resource
    private ChatSseExecutor chatSseExecutor;

    @Resource
    private ChatCancelRegistry chatCancelRegistry;

    @Resource
    private SseHeartbeat sseHeartbeat;

    // ==================== 提问 ====================

    @PostMapping("/ask")
    @Operation(summary = "统一提问：按 mode 走问数或知识库问答（同步）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<UnifiedChatAskRespVO> ask(@Valid @RequestBody UnifiedChatAskReqVO reqVO) {
        return success(chatService.ask(reqVO));
    }

    /**
     * 统一流式提问：mode=QUERY 用问数阶段序列，mode=KNOWLEDGE 用知识库阶段序列。
     *
     * <p>事件名有两种：
     * <ul>
     *   <li>{@code stage}：链路阶段（intent → context → recalled → generated → validated → done）；</li>
     *   <li>{@code delta}：知识库问答的正文增量，逐段推送。</li>
     * </ul>
     * {@code done} 事件的 payload 仍带完整 answer，老前端忽略 delta 也能正常工作。
     *
     * <p>空闲时服务端会推 SSE 注释帧（{@code :ping}）保活，它不产生 {@code event:}/{@code data:} 行，
     * 前端按行解析时忽略即可。客户端断开（关页面 / AbortController）会取消本轮生成并以 STOPPED 落库。
     */
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "统一提问流式版：按阶段推送 SSE 事件")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public SseEmitter askStream(@Valid @RequestBody UnifiedChatAskReqVO reqVO, HttpServletResponse response) {
        boolean knowledge = ChatModeEnum.isKnowledge(reqVO.getMode());
        long timeout = resolveTimeout(knowledge);
        boolean enabled = resolveEnabled(knowledge);
        // 反向代理（nginx 默认 proxy_buffering on）会攒够一批才转发，前端看到的仍是「一次性蹦出」，
        // 流式等于白做。这两个头是流式生效的前提，不是可选项。
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        SseEmitter emitter = new SseEmitter(timeout);

        if (!enabled) {
            reject(emitter, AI_CHAT_SSE_DISABLED.getMsg(), AI_CHAT_SSE_DISABLED.getCode());
            return emitter;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        boolean tenantIgnore = TenantContextHolder.isIgnore();
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (tenantId == null || loginUser == null) {
            reject(emitter, "缺少租户或登录上下文", AI_CHAT_SSE_DISABLED.getCode());
            return emitter;
        }

        // 取消令牌**无条件**创建：clientRequestId 缺失、或 /cancel 开关关闭时，
        // 仍然要支持「客户端断连即取消」，否则关页面后模型会跑完整段、token 照常计费。
        // 只有「开关开启 + 有 clientRequestId」才登记进注册表（即可被 /cancel 定位）。
        String clientRequestId = reqVO.getClientRequestId();
        ChatCancelToken cancelToken = chatCancelRegistry.registerForConnection(
                tenantId, clientRequestId, TracerUtils.getTraceId());

        ChatStreamConnection connection = new ChatStreamConnection(emitter, cancelToken);
        ChatStageSink sink = connection.sink();

        // 断连兜底：Servlet 的 SseEmitter 没有 onClose，onError 只在写失败时被触发；
        // onTimeout 是容器超时回调。两者都走 markBroken，与 send 失败共用同一条取消路径。
        emitter.onError(ex -> connection.markBroken(ChatCancelToken.Reason.CLIENT_GONE));
        emitter.onTimeout(() -> connection.markBroken(ChatCancelToken.Reason.TIMEOUT));

        sseHeartbeat.start(connection);

        try {
            chatSseExecutor.submit(tenantId, tenantIgnore, loginUser, () -> {
                try {
                    ChatCancelContext.set(cancelToken);
                    chatService.ask(reqVO, sink);
                } catch (Exception ex) {
                    sink.emit(errorEvent("对话链路异常，详见服务端日志", null));
                } finally {
                    ChatCancelContext.clear();
                    chatCancelRegistry.remove(tenantId, clientRequestId);
                    // 先停心跳再 complete，否则收尾竞态会写出 IllegalStateException
                    connection.close();
                }
            });
        } catch (RejectedExecutionException ex) {
            chatCancelRegistry.remove(tenantId, clientRequestId);
            sink.emit(errorEvent(AI_CHAT_SSE_BUSY.getMsg(), AI_CHAT_SSE_BUSY.getCode()));
            connection.close();
        }
        return emitter;
    }

    /**
     * 停止回答：按 clientRequestId 定位在途请求并触发取消。
     * 请求已结束、id 不存在或未开启取消能力时返回明确错误，便于前端收口。
     */
    @PostMapping("/cancel")
    @Operation(summary = "停止回答：按客户端请求编号取消在途的流式提问")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<Boolean> cancel(@RequestParam("clientRequestId") String clientRequestId) {
        Long tenantId = TenantContextHolder.getTenantId();
        boolean ok = chatCancelRegistry.cancel(tenantId, clientRequestId);
        if (!ok) {
            throw exception(CHAT_CANCEL_NOT_FOUND);
        }
        return success(true);
    }

    // ==================== 会话 ====================

    @GetMapping("/session/page")
    @Operation(summary = "我的会话列表（两种模式聚合，分页）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<PageResult<UnifiedSessionRespVO>> pageSessions(@Valid UnifiedSessionPageReqVO reqVO) {
        return success(chatService.pageSessions(reqVO.getMode(), reqVO.getTitle(),
                reqVO.getPageNo(), reqVO.getPageSize()));
    }

    @PutMapping("/session/rename")
    @Operation(summary = "会话改名（按 mode 分流）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<Boolean> renameSession(@Valid @RequestBody ChatOperateReqVO.Rename reqVO) {
        chatService.renameSession(reqVO.getMode(), reqVO.getSessionId(), reqVO.getTitle());
        return success(true);
    }

    @DeleteMapping("/session/delete")
    @Operation(summary = "删除会话（按 mode 分流，级联软删消息）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<Boolean> deleteSession(@RequestParam("mode") String mode,
                                               @RequestParam("sessionId") Long sessionId) {
        chatService.deleteSession(mode, sessionId);
        return success(true);
    }

    // ==================== 消息 ====================

    @GetMapping("/message/list")
    @Operation(summary = "会话消息历史（当前主链）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<List<UnifiedChatMessageRespVO>> listMessages(@RequestParam("mode") String mode,
                                                                     @RequestParam("sessionId") Long sessionId) {
        return success(chatService.listMessages(mode, sessionId));
    }

    @GetMapping("/message/versions")
    @Operation(summary = "某轮回答的全部版本")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<List<UnifiedChatMessageRespVO>> listVersions(@RequestParam("mode") String mode,
                                                                     @RequestParam("messageId") Long messageId) {
        return success(chatService.listVersions(mode, messageId));
    }

    @GetMapping("/message/question-versions")
    @Operation(summary = "某一轮问题的全部版本")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<List<UnifiedChatMessageRespVO>> listQuestionVersions(@RequestParam("mode") String mode,
                                                                             @RequestParam("sessionId") Long sessionId,
                                                                             @RequestParam("seq") Integer seq) {
        return success(chatService.listQuestionVersions(mode, sessionId, seq));
    }

    @PutMapping("/message/versions")
    @Operation(summary = "切换版本：选中目标版本并推进当前分支叶子")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<Boolean> switchVersion(@Valid @RequestBody ChatOperateReqVO.SwitchVersion reqVO) {
        chatService.switchVersion(reqVO.getMode(), reqVO.getSessionId(), reqVO.getTargetMessageId());
        return success(true);
    }

    // ==================== 重跑 / 赞踩 ====================

    @PostMapping("/regenerate")
    @Operation(summary = "重新生成：以原问题重跑，旧版本退出主链")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<UnifiedChatAskRespVO> regenerate(@Valid @RequestBody ChatOperateReqVO.Regenerate reqVO) {
        return success(chatService.regenerate(reqVO.getMode(), reqVO.getMessageId(), reqVO.getModelConfigId()));
    }

    @PostMapping("/edit")
    @Operation(summary = "修改问题：改写问题后从该轮重跑，生成新分支")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<UnifiedChatAskRespVO> edit(@Valid @RequestBody ChatOperateReqVO.Edit reqVO) {
        return success(chatService.edit(reqVO.getMode(), reqVO.getMessageId(),
                reqVO.getQuestion(), reqVO.getModelConfigId()));
    }

    @PostMapping("/rating")
    @Operation(summary = "赞踩：-1 踩 / 0 取消 / 1 赞（幂等，仅助手消息）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<Boolean> rate(@Valid @RequestBody ChatOperateReqVO.Rating reqVO) {
        chatService.rate(reqVO.getMode(), reqVO.getMessageId(), reqVO.getRating());
        return success(true);
    }

    // ==================== 检索调试 ====================

    @PostMapping("/retrieve-preview")
    @Operation(summary = "检索调试：返回召回切片与分数（仅知识库模式有意义）")
    @PreAuthorize("@ss.hasPermission('data-query:chat:query')")
    public CommonResult<RetrievePreviewVO.Resp> retrievePreview(@Valid @RequestBody RetrievePreviewVO.Req reqVO) {
        KnowledgeRecallResult r = recallService.recall(reqVO.getBaseId(), reqVO.getQuestion(),
                reqVO.getTopK(), reqVO.getVersion());
        RetrievePreviewVO.Resp resp = new RetrievePreviewVO.Resp();
        resp.setMode(r.getMode());
        resp.setDegraded(r.getDegraded());
        resp.setDegradeReason(r.getDegradeReason());
        resp.setVectorCandidateCount(r.getVectorCandidateCount());
        resp.setKeywordCandidateCount(r.getKeywordCandidateCount());
        resp.setKeptCount(r.getKeptCount());
        resp.setTruncated(r.getTruncated());
        resp.setTokenEstimate(r.getTokenEstimate());
        resp.setContextText(r.getContextText());
        resp.setLatencyMs(r.getLatencyMs());
        resp.setRerankApplied(r.getRerankApplied());
        resp.setRerankSkipReason(r.getRerankSkipReason());
        resp.setRerankModelCode(r.getRerankModelCode());
        resp.setRerankLatencyMs(r.getRerankLatencyMs());
        resp.setItems(r.getItems());
        return success(resp);
    }

    // ==================== 工具 ====================

    private boolean resolveEnabled(boolean knowledge) {
        Boolean enabled = knowledge ? knowledgeProperties.getSseEnabled() : chatProperties.getSseEnabled();
        return !Boolean.FALSE.equals(enabled);
    }

    private long resolveTimeout(boolean knowledge) {
        Integer ms = knowledge ? knowledgeProperties.getSseTimeoutMs() : chatProperties.getSseTimeoutMs();
        long value = ms == null ? 70000L : ms.longValue();
        return value <= 0 ? 70000L : value;
    }

    private static ChatStageEvent errorEvent(String message, Integer errorCode) {
        return ChatStageEvent.of("error", ChatStageEvent.ERROR, message, 100)
                .put("status", "FAILED")
                .put("errorCode", errorCode);
    }

    /** 早退路径（开关关闭 / 上下文缺失）没有业务要跑：发一个错误事件即收尾。 */
    private static void reject(SseEmitter emitter, String message, Integer errorCode) {
        ChatStreamConnection rejected = new ChatStreamConnection(emitter, null);
        rejected.sink().emit(errorEvent(message, errorCode));
        rejected.close();
    }
}
