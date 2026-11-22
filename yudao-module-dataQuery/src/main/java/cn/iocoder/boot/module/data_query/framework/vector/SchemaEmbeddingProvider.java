package cn.iocoder.boot.module.data_query.framework.vector;

import java.util.List;

/**
 * Schema 向量写入侧的 Embedding 服务接口（P1 定义 SPI，P2 提供实现）。
 *
 * 口径：Embedding 账号【每租户独立】——由当前租户的 ai_model_config(model_type = 2)
 * + ai_model_scene_binding(EMBEDDING) 解析，凭证经 AiSecretTypeHandler 解密，
 * 调用经 M1 治理并写 ai_model_call_log；不使用平台统一 Key。
 *
 * 维度统一 1536 是 vector(1536) 列与 HNSW 索引的技术约束，不等于账号统一。
 * 写入侧与查询侧必须使用同一租户、同一模型、同一维度；换模型只需重建该租户向量。
 *
 * P1 阶段只有「未接线」默认实现：Document 仍会完整构建并落库，但 embedding 为 NULL、
 * sync_status = 0（待构建），status 接口明确暴露 pendingEmbedding 数量，绝不假装成功。
 * P2 注册自己的 Bean 即可自动覆盖（DataQueryAutoConfiguration 用 @ConditionalOnMissingBean）。
 */
public interface SchemaEmbeddingProvider {

    /** 当前租户是否已配置可用的 Embedding 模型与凭证 */
    boolean isAvailable(Long tenantId);

    /**
     * 当前租户正在使用的 Embedding 模型代号（写入 ai_schema_vector.embedding_model_code）。
     * 该值参与 content_hash，换供应商 / 换模型会自动触发本租户向量的增量重建。
     */
    String currentModelCode(Long tenantId);

    /** 向量维度，必须与 ai_schema_vector 的 vector(1536) 列定义一致 */
    int dimension();

    /**
     * 批量把文本转成 pgvector 字面量。
     *
     * @param tenantId 显式传入的租户编号，异步索引任务不得依赖 ThreadLocal
     * @param texts    待向量化的文本，顺序与返回值一一对应
     * @return 形如 "[0.1,0.2,...]" 的字面量列表，长度必须等于 texts
     */
    List<String> embed(Long tenantId, List<String> texts);

}
