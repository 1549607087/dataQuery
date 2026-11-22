package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * 数据源表元数据 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId，并按 datasource_id 校验归属（AiDatasourceOwnerValidator）。
 * 严禁使用 IRepository#getOne(Wrapper)：MP 3.5.17 走 selectCursor，与 Druid PSCache 冲突。
 */
public interface AiDatasourceTableService extends IService<AiDatasourceTable> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiDatasourceTable getAiDatasourceTable(Long id);

    Long createAiDatasourceTable(AiDatasourceTable entity);

    void updateAiDatasourceTable(AiDatasourceTable entity);

    void deleteAiDatasourceTable(Long id);

    void deleteAiDatasourceTableList(List<Long> ids);

    /** 按数据源查询（先校验数据源归属，再查本表） */
    List<AiDatasourceTable> listByDatasourceId(Long datasourceId);

    /** S3 向量召回取数：仅返回可见、可查且源端未删除的表 */
    List<AiDatasourceTable> listVisibleTables(Long datasourceId);

}
