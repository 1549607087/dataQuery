package cn.iocoder.boot.module.data_query.framework.chat;

import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次问答请求的取消令牌。
 *
 * <p>前端发起请求时携带 clientRequestId，服务端以 tenantId + clientRequestId 为 key 注册本令牌。
 * 它承载两类取消能力：
 * <ol>
 *   <li>协作式标志 {@link #cancelled}：编排器在每个阶段边界、治理层在每次重试/降级前检查，
 *       命中即停止后续 LLM 调用（这部分省 token 是确定的）；</li>
 *   <li>在途 HTTP 订阅 {@link #activeDisposables}：由模型客户端把 WebClient 的 Disposable 注册进来，
 *       {@link #cancel()} 时一并 dispose，断开 socket（非流式下是否即时省 token 取决于供应商）。</li>
 * </ol>
 *
 * <p>线程安全：cancel 可能来自 cancel 端点线程或 SSE onClose 回调线程，
 * 而注册来自模型调用线程，故标志用 AtomicBoolean、订阅列表用 CopyOnWriteArrayList。
 */
public class ChatCancelToken {

    /** 取消来源：只用于归因与日志，不影响「是否停止」这件事本身 */
    public enum Reason {
        /** 前端点了停止 */
        USER,
        /** 客户端连接已断（关页面 / abort），由 SSE 写失败发现 */
        CLIENT_GONE,
        /** SSE 连接超时 */
        TIMEOUT
    }

    private final Long tenantId;
    private final String clientRequestId;
    private final long createdAt = System.currentTimeMillis();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Reason> cancelReason = new AtomicReference<>();
    private final List<Disposable> activeDisposables = new CopyOnWriteArrayList<>();

    public ChatCancelToken(Long tenantId, String clientRequestId) {
        this.tenantId = tenantId;
        this.clientRequestId = clientRequestId;
    }

    /** 触发取消（来源记为 USER）：置标志 + dispose 全部在途 HTTP 订阅；幂等，先到者定因。 */
    public void cancel() {
        cancel(Reason.USER);
    }

    /**
     * 带原因的取消。原因采用 first-wins：断连与用户点停止可能几乎同时发生，
     * 以先发现的那个为准，后到的调用不再覆盖（更接近事实，也避免日志口径来回跳）。
     */
    public void cancel(Reason reason) {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        cancelReason.set(reason == null ? Reason.USER : reason);
        for (Disposable disposable : activeDisposables) {
            try {
                disposable.dispose();
            } catch (Exception ignored) {
                // dispose 失败不影响其它订阅与协作式标志
            }
        }
        activeDisposables.clear();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** 取消来源；未取消时为 null */
    public Reason getCancelReason() {
        return cancelReason.get();
    }

    /**
     * 注册一个在途 HTTP 订阅。若令牌已取消，立即 dispose 该订阅（覆盖「取消发生在注册之前」的竞态）。
     */
    public void register(Disposable disposable) {
        if (disposable == null) {
            return;
        }
        if (cancelled.get()) {
            try {
                disposable.dispose();
            } catch (Exception ignored) {
            }
            return;
        }
        activeDisposables.add(disposable);
    }

    /** 调用结束（成功/失败）时移除订阅，避免令牌持有已完成的引用。 */
    public void unregister(Disposable disposable) {
        if (disposable != null) {
            activeDisposables.remove(disposable);
        }
    }

    /** 阶段边界检查：已取消则抛出，交由编排器以 STOPPED 收口。 */
    public void checkCancelled() {
        if (cancelled.get()) {
            throw new CancelledException(clientRequestId);
        }
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
