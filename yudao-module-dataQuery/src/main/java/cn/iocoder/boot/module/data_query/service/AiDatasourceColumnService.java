package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * 数据源字段元数据 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId，并按 datasource_id 校验归属（AiDatasourceOwnerValidator）。
 * 严禁使用 IRepository#getOne(Wrapper)：MP 3.5.17 走 selectCursor，与 Druid PSCache 冲突。
 */
public interface AiDatasourceColumnService extends IService<AiDatasourceColumn> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiDatasourceColumn getAiDatasourceColumn(Long id);

    Long createAiDatasourceColumn(AiDatasourceColumn entity);

    void updateAiDatasourceColumn(AiDatasourceColumn entity);

    void deleteAiDatasourceColumn(Long id);

    void deleteAiDatasourceColumnList(List<Long> ids);

    /** 按数据源查询（先校验数据源归属，再查本表） */
    List<AiDatasourceColumn> listByDatasourceId(Long datasourceId);

    /** S3 向量召回取数：仅返回可见、可查且源端未删除的列 */
    List<AiDatasourceColumn> listVisibleColumns(Long tableId);

    /** 按数据源取全量可见列，供列级 Document 生成 */
    List<AiDatasourceColumn> listVisibleColumnsByDatasource(Long datasourceId);

}
