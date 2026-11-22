package cn.iocoder.boot.module.data_query.framework.vector;

import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_NOT_CONFIGURED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * P1 默认的 Embedding 占位实现：明确表示「尚未接线」，不伪造向量。
 *
 * 由 DataQueryAutoConfiguration 以 @ConditionalOnMissingBean 注册，
 * P2 接入按租户动态构造的真实 Embedding 客户端后，注册自己的 Bean 即自动覆盖，
 * 向量写入任务与 Document 构建逻辑无需任何改动。
 */
public class UnconfiguredSchemaEmbeddingProvider implements SchemaEmbeddingProvider {

    /** 未接线占位代号；参与 content_hash，P2 接入后会自动触发全量重建 */
    public static final String PENDING_MODEL_CODE = "PENDING";

    @Override
    public boolean isAvailable(Long tenantId) {
        return false;
    }

    @Override
    public String currentModelCode(Long tenantId) {
        return PENDING_MODEL_CODE;
    }

    @Override
    public int dimension() {
        return 1536;
    }

    @Override
    public List<String> embed(Long tenantId, List<String> texts) {
        throw exception(SCHEMA_EMBEDDING_NOT_CONFIGURED);
    }

}
