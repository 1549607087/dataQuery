package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;

import java.util.UUID;

/**
 * 联调用的回声 ModelClient（占位实现）
 *
 * ⚠️ 仅用于验证「限流 → 熔断 → 超时 → 重试 → 降级 → 日志埋点」链路是否打通，
 *    不发起任何真实 LLM 请求，返回内容不是业务结果。接入真实供应商客户端后，
 *    由 @ConditionalOnMissingBean 自动让位，无需改动本模块任何治理代码。
 *
 * 识别方式：返回的 ModelInvocationResult.echo = true。
 *
 * 同时实现 {@link StreamModelClient}：流式链路找不到真实流式客户端时，
 * 这里退化为「一次推完整文本」的单增量流式，前端仍能拿到完整结果（只是没有逐字效果），
 * 保证 data-query.chat.enabled=false 的运维降级路径不会因为缺 SPI 实现而直接报错。
 */
public class EchoModelClient implements ModelClient, StreamModelClient {

    @Override
    public ModelInvocationResult invoke(ModelInvocation invocation, AiModelConfig config) {
        String prompt = invocation.getPrompt() == null ? "" : invocation.getPrompt();
        int requestTokens = Math.max(1, prompt.length() / 4);
        String content = "[ECHO] scene=" + invocation.getSceneCode()
                + ", model=" + config.getModelId()
                + ", promptLength=" + prompt.length()
                + " —— 这是联调占位实现，请接入真实 ModelClient";
        int responseTokens = Math.max(1, content.length() / 4);

        ModelInvocationResult result = new ModelInvocationResult();
        result.setContent(content);
        result.setRequestTokens(requestTokens);
        result.setResponseTokens(responseTokens);
        result.setTotalTokens(requestTokens + responseTokens);
        result.setRequestId("echo-" + UUID.randomUUID().toString().replace("-", ""));
        result.setModelId(config.getModelId());
        result.setModelConfigId(config.getId());
        result.setEcho(Boolean.TRUE);
        return result;
    }

    /**
     * 单增量流式：一次回调推完整文本。
     *
     * <p>哨兵实现，不模拟逐字延迟 —— 联调时「能不能跑通」比「像不像真的」重要，
     * 人为 sleep 只会拖慢单测与端到端联调。
     */
    @Override
    public ModelInvocationResult stream(ModelInvocation invocation, AiModelConfig config, DeltaSink sink)
            throws Exception {
        ModelInvocationResult result = invoke(invocation, config);
        sink.onDelta(result.getContent());
        return result;
    }

}
