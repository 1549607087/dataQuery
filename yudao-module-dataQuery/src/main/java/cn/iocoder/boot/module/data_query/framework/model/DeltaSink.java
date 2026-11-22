package cn.iocoder.boot.module.data_query.framework.model;

/**
 * 流式增量输出口。
 *
 * <p>由调用方（治理层 → 编排器）实现，模型客户端每收到一段增量就回调一次。
 * 约定：
 * <ol>
 *   <li>实现方必须尽快返回，回调里不做重活（引用门控是纯字符扫描，符合要求）；</li>
 *   <li>实现方抛异常视为「下游已不需要」：客户端应立即 dispose 上游订阅断开连接；
 *       但已经产生的输出不可回滚，调用方要以「已提交」语义收口，不得再重试或换模型。</li>
 * </ol>
 */
@FunctionalInterface
public interface DeltaSink {

    /**
     * 收到一段增量文本。
     *
     * @param delta 非空增量（可能包含标点、换行；不保证是完整句子）
     */
    void onDelta(String delta) throws Exception;
}
