package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.AiSchemaVectorPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.AiSchemaVectorRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorBuildRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorStatusRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

/**
 * Schema 向量索引写入侧 Service（P1）
 *
 * 只负责「元数据 -> Document -> content_hash 增量 upsert -> 下线清理」，
 * 查询侧召回器属于 P3；真实 Embedding 客户端属于 P2（SchemaEmbeddingProvider SPI）。
 */
public interface SchemaVectorBuildService {

    /**
     * 构建 / 增量更新某数据源的向量索引。
     *
     * @param rebuild true 时忽略 content_hash 强制重新 Embedding 全部 Document（sync_type = 8）
     */
    SchemaVectorBuildRespVO build(Long datasourceId, boolean rebuild);

    /** 索引新鲜度与 Embedding 接线状态 */
    SchemaVectorStatusRespVO status(Long datasourceId);

    /** 分页查看向量行（不返回 embedding 字面量） */
    PageResult<AiSchemaVectorRespVO> page(AiSchemaVectorPageReqVO reqVO);

}
