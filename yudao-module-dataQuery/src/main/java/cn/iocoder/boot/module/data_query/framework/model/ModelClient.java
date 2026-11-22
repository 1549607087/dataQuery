package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;

/**
 * 大模型调用 SPI
 *
 * 治理层（ModelGovernanceExecutor）只负责「用哪个模型、限不限流、熔不熔断、失败了怎么办」，
 * 真正的 HTTP 调用由本接口的实现完成（OpenAI 兼容 / 通义 / 豆包 / DeepSeek / 本地 vLLM 等）。
 *
 * 实现约定：
 * 1) 必须按 invocation.getTimeoutMs() 或 config 的超时设置 HTTP 客户端的 connect/read timeout。
 *    治理层用 Future.get 做硬超时，但只有客户端响应中断才能真正释放连接。
 * 2) 失败时优先抛 ModelException 并带上准确的 ModelErrorCategory，
 *    否则治理层只能按异常消息关键字兜底分类。
 * 3) 返回 ModelInvocationResult 时填好 token 数与供应商 requestId，用于对账与成本统计。
 * 4) 流式输出场景后续可另开接口，本 SPI 面向一次性同步调用。
 */
public interface ModelClient {

    /**
     * 执行一次模型调用
     *
     * @param invocation 调用上下文（含场景、提示词、traceId、超时等）
     * @param config     本次路由选中的模型配置（含 modelId、供应商、默认参数）
     * @return 调用结果
     * @throws Exception 实现方可抛任意异常，由治理层统一分类
     */
    ModelInvocationResult invoke(ModelInvocation invocation, AiModelConfig config) throws Exception;

}
