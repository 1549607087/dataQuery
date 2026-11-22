package cn.iocoder.boot.module.data_query.framework.model;

/**
 * 下游主动停止消费流式结果的信号（不是故障）。
 *
 * <p>唯一用途：回答达到 max-answer-chars 后让供应商也停止生成。这与「用户取消」不同——
 * 取消走 {@code ChatCancelToken}；也不同于「下游写失败」——那是故障，要计入熔断。
 *
 * <p>不填堆栈：主动控制流会在正常请求里每 8000 字触发一次，填堆栈只是白费开销。
 * 从 {@code DeltaSink.onDelta} 抛出后，Reactor 会自动 cancel 上游订阅（断开供应商 socket），
 * 模型客户端把它转成成功的 {@code ModelInvocationResult.aborted=true}，治理层不重试、不计失败。
 */
public class StreamAbortSignal extends RuntimeException {

    public StreamAbortSignal(String reason) {
        super(reason, null, false, false);
    }
}
