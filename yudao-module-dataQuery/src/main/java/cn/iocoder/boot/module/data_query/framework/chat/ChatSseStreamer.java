package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_SSE_BUSY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_SSE_DISABLED;

/**
 * SSE 连接的统一装配：三个流式入口共用这一处，不再各自复制 60 行管线代码。
 *
 * <p>职责边界：只管「连接怎么建立、什么时候取消、怎么收尾」，不碰业务分支。
 * 业务差异通过 {@code action} 注入——这里是唯一决定「本次跑 ask 还是 regenerate」的地方。
 *
 * <p>错误码保真：业务异常（如「消息不存在或无权访问」）必须把原生 code 发到前端，
 * 否则同步端点能给出精确错误码、流式端点却只剩「对话链路异常」，属于回归。
 */
@Slf4j
@Component
public class ChatSseStreamer {

    @Resource
    private ChatCancelRegistry chatCancelRegistry;
    @Resource
    private SseHeartbeat sseHeartbeat;
    @Resource
    private ChatSseExecutor chatSseExecutor;
    @Resource
    private KnowledgeProperties knowledgeProperties;
    @Resource
    private ChatOrchestratorProperties chatProperties;
    @Resource
    private ChatInflightGuard inflightGuard;

    /**
     * 反向代理（nginx 默认 proxy_buffering on）会攒够一批才转发，前端看到的仍是「一次性蹦出」，
     * 流式等于白做。这两个头是流式生效的前提，不是可选项。
     */
    public void prepare(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
    }

    /** @param action 真正跑业务的地方；所有输出必须经 sink，不要自己持有 emitter */
    public SseEmitter stream(StreamSpec spec, Consumer<ChatStageSink> action) {
        long timeout = resolveTimeout(spec.knowledge());
        SseEmitter emitter = new SseEmitter(timeout);

        if (!resolveEnabled(spec.knowledge())) {
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

        // 重跑在途去重：冲突在这里直接抛 124，连接还没建立，前端按普通 HTTP 错误处理即可。
        // ASK 传的 op/sourceMessageId 为 null，guard 内部直接放行，连点两次发送仍是两轮提问。
        Long userId = loginUser.getId();
        String inflightToken = inflightGuard.acquire(tenantId, userId, spec.op(), spec.sourceMessageId());

        // 取消令牌无条件创建：clientRequestId 缺失或 /cancel 开关关闭时仍要支持断连取消，
        // 否则关页面后模型会跑完整段、token 照常计费。只有可被 /cancel 定位才登记进注册表。
        String clientRequestId = spec.clientRequestId();
        ChatCancelToken cancelToken = chatCancelRegistry.registerForConnection(
                tenantId, clientRequestId, TracerUtils.getTraceId());

        ChatStreamConnection connection = new ChatStreamConnection(emitter, cancelToken);
        ChatStageSink sink = connection.sink();
        // Servlet SseEmitter 没有 onClose；onError 只在写失败时触发，onTimeout 是容器超时回调
        emitter.onError(ex -> connection.markBroken(ChatCancelToken.Reason.CLIENT_GONE));
        emitter.onTimeout(() -> connection.markBroken(ChatCancelToken.Reason.TIMEOUT));
        sseHeartbeat.start(connection);

        try {
            chatSseExecutor.submit(tenantId, tenantIgnore, loginUser, () -> {
                try {
                    ChatCancelContext.set(cancelToken);
                    action.accept(sink);
                } catch (ServiceException ex) {
                    log.warn("[chat][stream][business-error] traceId={} code={} msg={}",
                            TracerUtils.getTraceId(), ex.getCode(), ex.getMessage());
                    sink.emit(errorEvent(ex.getMessage(), ex.getCode()));
                } catch (CancelledException ex) {
                    // 正常收口路径：编排器已把 STOPPED 与部分回答落库
                    log.info("[chat][stream][cancelled] clientRequestId={}", clientRequestId);
                } catch (Exception ex) {
                    log.error("[chat][stream] 服务端异常 clientRequestId={}", clientRequestId, ex);
                    sink.emit(errorEvent("对话链路异常，详见服务端日志", null));
                } finally {
                    ChatCancelContext.clear();
                    chatCancelRegistry.remove(tenantId, clientRequestId);
                    inflightGuard.release(tenantId, userId, spec.op(), spec.sourceMessageId(), inflightToken);
                    // 先停心跳再 complete，否则收尾竞态会写出 IllegalStateException
                    connection.close();
                }
            });
        } catch (RejectedExecutionException ex) {
            chatCancelRegistry.remove(tenantId, clientRequestId);
            inflightGuard.release(tenantId, userId, spec.op(), spec.sourceMessageId(), inflightToken);
            sink.emit(errorEvent(AI_CHAT_SSE_BUSY.getMsg(), AI_CHAT_SSE_BUSY.getCode()));
            connection.close();
        }
        return emitter;
    }

    private boolean resolveEnabled(boolean knowledge) {
        Boolean enabled = knowledge ? knowledgeProperties.getSseEnabled() : chatProperties.getSseEnabled();
        return !Boolean.FALSE.equals(enabled);
    }

    private long resolveTimeout(boolean knowledge) {
        Integer ms = knowledge ? knowledgeProperties.getSseTimeoutMs() : chatProperties.getSseTimeoutMs();
        long value = ms == null ? 70000L : ms.longValue();
        return value <= 0 ? 70000L : value;
    }

    /** 所有 SSE 输出都经 ChatStreamConnection，早退路径也不留裸 emitter.send。 */
    private static void reject(SseEmitter emitter, String message, Integer errorCode) {
        ChatStreamConnection rejected = new ChatStreamConnection(emitter, null);
        rejected.sink().emit(errorEvent(message, errorCode));
        rejected.close();
    }

    private static ChatStageEvent errorEvent(String message, Integer errorCode) {
        return ChatStageEvent.of("error", ChatStageEvent.ERROR, message, 100)
                .put("status", "FAILED")
                .put("errorCode", errorCode);
    }
}
