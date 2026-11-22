package cn.iocoder.boot.module.data_query.framework.knowledge.rerank;

import java.util.List;

/**
 * 知识库召回专用 Rerank（Cross-Encoder）SPI。
 *
 * <p>与 {@code ModelClient}（chat completion 语义：单个 prompt → content 字符串）不同，
 * Rerank 的输入是「一个 query + 一组 documents」，输出是每个文档的相关性分数数组。
 * 因此这里仿 {@code SchemaEmbeddingProvider} 单列 SPI，由实现自行复用治理三件套
 * （限流 / 熔断 / 调用日志），不塞进 chat 的 ModelGovernanceExecutor。
 *
 * <p>所有方法都显式带 tenantId：调用方在 recall 的同步线程里执行，但实现内部解析凭证时
 * 仍按租户隔离，绝不读全局 Key。失败一律抛异常，由召回侧吞掉降级，绝不伪造分数。
 */
public interface KnowledgeRerankProvider {

    /** 当前租户是否配置了可用的 RERANK 模型绑定（model_type=3）与凭证 */
    boolean isAvailable(Long tenantId);

    /** 当前租户 Rerank 模型代号（provider.code:modelId），仅观测用；未配置为 PENDING */
    String currentModelCode(Long tenantId);

    /**
     * 对一组候选切片按与 query 的相关性打分。
     *
     * @param documents 候选文本，顺序与返回的 scores 下标一一对应
     * @return 结果中 scores[i] 即 documents[i] 的相关性分数（越大越相关）
     */
    RerankResult rerank(Long tenantId, String query, List<String> documents);

}
