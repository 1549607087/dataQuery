package cn.iocoder.boot.module.data_query.framework.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

/**
 * 一条 SSE 连接的生命周期封装。
 *
 * <p>它把「写事件 / 心跳 / 断连检测 / 取消」收敛到同一个对象上，原因很实际：
 * <ul>
 *   <li>心跳与增量必须写同一个 emitter，两套 sink 会出现「心跳还在推、连接已关闭」；</li>
 *   <li>断连只有一个可靠信号——<b>写失败</b>。Servlet 的 {@link SseEmitter} 没有 onClose，
 *       {@code onError} 也只在发送抛异常时被触发，所以「谁写谁发现」必须集中在一处。</li>
 * </ul>
 *
 * <p>状态只有三个：{@code OPEN → BROKEN}（客户端消失）、{@code OPEN → CLOSED}（正常收尾）。
 * 进入 BROKEN 后不再尝试写；进入 CLOSED 后心跳立即停止，避免收尾竞态写出
 * {@code IllegalStateException("ResponseBodyEmitter has already completed")}。
 *
 * <p><b>为什么 sink 不向上抛异常</b>：从 sink 抛出的异常会被治理层记成下游失败并计入熔断失败率，
 * 而「用户关掉了页面」不是下游故障。这里捕获 → 标记断连 → 由取消令牌传播，统计口径保持干净。
 */
@Slf4j
public final class ChatStreamConnection {

    private final SseEmitter emitter;
    private final ChatCancelToken cancelToken;
    private final ChatStageSink sink;

    /** 串行化「状态判断 + 写」，避免心跳与增量交错时把 broken/closed 判断做在两写之间 */
    private final Object lock = new Object();

    private volatile long lastWriteNanos = System.nanoTime();
    private volatile boolean broken;
    private volatile boolean closed;
    private volatile ScheduledFuture<?> heartbeat;

    public ChatStreamConnection(SseEmitter emitter, ChatCancelToken cancelToken) {
        this.emitter = emitter;
        this.cancelToken = cancelToken;
        this.sink = new ChatStageSink() {
            @Override
            public void emit(ChatStageEvent event) {
                send(SseEmitter.event().name("stage").data(event, MediaType.APPLICATION_JSON));
            }

            @Override
            public void delta(ChatDeltaEvent event) {
                send(SseEmitter.event().name("delta").data(event, MediaType.APPLICATION_JSON));
            }
        };
    }

    /** 交给编排器的输出口：始终返回同一实例，调用方不必判空。 */
    public ChatStageSink sink() {
        return sink;
    }

    /**
     * 启动心跳。由 {@link SseHeartbeat} 调度，本对象只持有句柄用于收尾时取消。
     */
    void attachHeartbeat(ScheduledFuture<?> future) {
        synchronized (lock) {
            if (closed || broken) {
                future.cancel(false);
                return;
            }
            this.heartbeat = future;
        }
    }

    /** 空闲到点才发注释帧：模型高频出字时 delta 本身就是心跳，盲目写只会增加锁竞争。 */
    void pingIfIdle(long idleNanos) {
        if (broken || closed) {
            return;
        }
        if (System.nanoTime() - lastWriteNanos < idleNanos) {
            return;
        }
        send(SseEmitter.event().comment("ping"));
    }

    /** 客户端已消失：停止心跳并触发取消，让上游 socket 断开、供应商停止生成。 */
    public void markBroken(ChatCancelToken.Reason reason) {
        synchronized (lock) {
            if (closed || broken) {
                return;
            }
            broken = true;
            stopHeartbeatLocked();
        }
        cancelUpstream(reason == null ? ChatCancelToken.Reason.CLIENT_GONE : reason);
    }

    /** 业务收尾：停心跳 → 置 CLOSED → complete。幂等。 */
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            stopHeartbeatLocked();
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 连接已关闭 / 已超时，容器会自行清理
        }
    }

    private void send(SseEmitter.SseEventBuilder builder) {
        boolean disconnected = false;
        synchronized (lock) {
            if (broken || closed) {
                return;
            }
            try {
                emitter.send(builder);
                lastWriteNanos = System.nanoTime();
            } catch (Exception ex) {
                if (isDisconnect(ex)) {
                    broken = true;
                    stopHeartbeatLocked();
                    disconnected = true;
                } else {
                    log.debug("[chat][sse] 事件发送失败（非断连，已忽略）: {}", ex.toString());
                }
            }
        }
        // 取消动作必须在锁外：cancel 会 dispose 上游订阅，可能触发其它回调
        if (disconnected) {
            cancelUpstream(ChatCancelToken.Reason.CLIENT_GONE);
        }
    }

    private void cancelUpstream(ChatCancelToken.Reason reason) {
        log.info("[chat][sse] 连接不可用，终止生成 reason={} clientRequestId={}",
                reason, cancelToken == null ? "-" : cancelToken.getClientRequestId());
        if (cancelToken != null) {
            cancelToken.cancel(reason);
        }
    }

    private void stopHeartbeatLocked() {
        ScheduledFuture<?> future = heartbeat;
        heartbeat = null;
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 只有 IO 类异常才算断连。
     *
     * <p>把「所有发送异常」都当断连会误伤：{@code complete()} 之后的心跳写入抛
     * {@code IllegalStateException("ResponseBodyEmitter has already completed")}，
     * 那只是自身收尾竞态，误判会把一次正常结束记成 {@code CLIENT_GONE}。
     */
    static boolean isDisconnect(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof IOException) {
                // Tomcat 的 ClientAbortException 继承 IOException
                return true;
            }
            if (cur.getClass().getName().endsWith("AsyncRequestNotUsableException")) {
                return true;
            }
            if (cur instanceof IllegalStateException) {
                return false;
            }
            if (cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
        }
        return false;
    }

    boolean isBroken() {
        return broken;
    }

    boolean isClosed() {
        return closed;
    }
}
