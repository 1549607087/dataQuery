package cn.iocoder.boot.module.data_query.framework.chat;

/**
 * SSE 输出口（阶段事件 + 正文增量）。
 *
 * 编排器只依赖这个接口：同步 /ask 传 null（零开销、行为与 P5 完全一致），
 * SSE /ask-stream 传入 SseEmitter 适配器。实现方必须保证 emit / delta 不抛出业务异常，
 * 编排器侧也会吞掉发送异常——客户端断流不能中断已经在执行的问数与审计留痕。
 *
 * <p>为什么把 delta 直接加在同一个接口上而不是另开旁路 sink：
 * 断流取消、心跳、取消令牌都要控制同一个 emitter，两个 sink 意味着两份 emitter 引用与两处
 * 生命周期管理，容易出现「心跳还在推、连接已关闭」这类不一致。默认实现为空方法，
 * 老的 lambda 实现（同步链路 / 单测）无需改动。
 */
@FunctionalInterface
public interface ChatStageSink {

    void emit(ChatStageEvent event);

    /**
     * 推送一段正文增量。同步链路与不支持流式的模式走默认空实现（增量为空操作）。
     */
    default void delta(ChatDeltaEvent event) {
        // 默认不输出：同步 /ask 与 QUERY 模式不产生 delta 事件
    }
}
