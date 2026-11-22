package cn.iocoder.boot.module.data_query.framework.chat;

/**
 * 当前 ask 请求的取消令牌上下文（线程本地）。
 *
 * <p>只在 SSE 流式 ask 的任务线程里设置：Controller 注册令牌后 set，任务 finally 中清除。
 * 编排器构造 {@link cn.iocoder.boot.module.data_query.framework.model.ModelInvocation} 时
 * 通过 {@link #current()} 取出并注入，从而在阶段边界与治理层传递取消信号。
 *
 * <p>同步 ask / regenerate / edit 等路径不设置本上下文，current() 返回 null，
 * 等价于「不可取消」，行为与改造前完全一致。模型调用发生在治理层独立线程池，
 * 但令牌已随 invocation 对象传入，不依赖 ThreadLocal 跨线程传播。
 */
public final class ChatCancelContext {

    private static final ThreadLocal<ChatCancelToken> HOLDER = new ThreadLocal<>();

    private ChatCancelContext() {
    }

    public static void set(ChatCancelToken token) {
        if (token == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(token);
        }
    }

    public static ChatCancelToken current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
