package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;

/**
 * 流式模型调用 SPI（与 {@link ModelClient} 并列，互不影响）。
 *
 * <p>为什么要单独开一个接口而不是给 {@code ModelClient} 加方法：
 * 同步调用与流式调用的失败语义完全不同 —— 同步调用失败了整段重来没有副作用，
 * 流式调用一旦把首段文本推给用户，就不能再重试或换模型（会拼出前后不一致的混合文本）。
 * 两种语义混在一个方法里，实现方很容易在流式分支里误用同步逻辑。
 *
 * <p>实现约定：
 * <ol>
 *   <li>每收到一段增量必须回调 {@link DeltaSink#onDelta(String)}，回调完成后再继续读下一段；</li>
 *   <li>正常结束时返回累积的完整结果（content / usage / requestId），与同步调用同口径；</li>
 *   <li>失败必须抛 {@link ModelException} 并带准确的 {@link ModelErrorCategory}，
 *       治理层据此决定提交前是否重试、提交后是否直接收口；</li>
 *   <li>必须响应 {@code invocation.getCancelToken()}：取消后立即 dispose 上游订阅并抛出
 *       {@code CancelledException}，让用户点「停止」能真正停止 token 消耗。</li>
 * </ol>
 */
public interface StreamModelClient {

    /**
     * 执行一次流式模型调用。
     *
     * @param invocation 调用上下文（含场景、提示词、traceId、超时、取消令牌）
     * @param config     本次路由选中的模型配置
     * @param sink       增量输出口；实现方不得缓存 delta 一次性回调
     * @return 累积后的完整结果
     * @throws Exception 实现方可抛任意异常，由治理层统一分类
     */
    ModelInvocationResult stream(ModelInvocation invocation, AiModelConfig config, DeltaSink sink) throws Exception;
}
