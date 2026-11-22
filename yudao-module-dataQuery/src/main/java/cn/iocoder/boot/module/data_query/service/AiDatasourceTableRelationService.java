package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceTableRelation;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * 表关系 / Join 路径 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId，并按 datasource_id 校验归属（AiDatasourceOwnerValidator）。
 * 严禁使用 IRepository#getOne(Wrapper)：MP 3.5.17 走 selectCursor，与 Druid PSCache 冲突。
 */
public interface AiDatasourceTableRelationService extends IService<AiDatasourceTableRelation> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiDatasourceTableRelation getAiDatasourceTableRelation(Long id);

    Long createAiDatasourceTableRelation(AiDatasourceTableRelation entity);

    void updateAiDatasourceTableRelation(AiDatasourceTableRelation entity);

    void deleteAiDatasourceTableRelation(Long id);

    void deleteAiDatasourceTableRelationList(List<Long> ids);

    /** 按数据源查询（先校验数据源归属，再查本表） */
    List<AiDatasourceTableRelation> listByDatasourceId(Long datasourceId);

    /** 多表 SQL 生成：取同数据源内已验证、已启用的 Join 路径 */
    List<AiDatasourceTableRelation> listVerifiedRelations(Long datasourceId, Collection<Long> tableIds);

    /**
     * 一跳关联扩展用：取同数据源内已验证、已启用、未下线的全部关系（不限制两端都在集合内）。
     * 调用方需自行用「可见可查表」元数据二次核验后，才能把关联表加入选集。
     */
    List<AiDatasourceTableRelation> listVerifiedRelationsForExpand(Long datasourceId);

}
