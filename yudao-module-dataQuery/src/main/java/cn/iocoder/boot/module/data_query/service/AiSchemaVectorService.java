package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiSchemaVector;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * Schema 向量索引 Service
 *
 * 全部方法都按 datasource_id 校验归属：向量是数据源元数据的派生物，
 * 不允许把 A 租户的向量写到 B 租户的数据源下。
 */
public interface AiSchemaVectorService extends IService<AiSchemaVector> {

    /** 按主键查询（selectList + LIMIT 1，规避 MP 3.5.17 的 selectCursor） */
    AiSchemaVector getVector(Long id);

    /** 列出某数据源的全部有效向量（含待构建 / 下线状态，供增量比对） */
    List<AiSchemaVector> listByDatasource(Long datasourceId);

    /** 统计某数据源的有效向量行数 */
    long countByDatasource(Long datasourceId);

    Long createVector(AiSchemaVector entity);

    void updateVector(AiSchemaVector entity);

    void deleteVector(Long id);

    /**
     * 把不在 keepIds 中的向量标记为下线（deleted = 1、sync_status = 4）。
     * 用于源端表 / 列被删除、visible / queryable 被关、mask_rule 改为 forbid 之后清理索引。
     */
    int retireVectors(Long datasourceId, Collection<Long> keepIds);

}
