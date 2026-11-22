package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceTestRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 数据源（连接配置与查询策略）Service
 *
 * 数据源主表与 5 张元数据表全部租户私有：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId；严禁使用 IRepository#getOne(Wrapper)（MP 3.5.17 + Druid PSCache 冲突）。
 */
public interface AiDatasourceService extends IService<AiDatasource> {

    /** 按主键查询未删除记录（selectList + LIMIT 1） */
    AiDatasource getDatasource(Long id);

    /** 查询并断言属于当前租户，供元数据归属校验与运行时取数使用 */
    AiDatasource getDatasourceOwned(Long id);

    Long createDatasource(AiDatasource entity);

    void updateDatasource(AiDatasource entity);

    /** 逻辑删除主表；元数据保留但因其 datasource_id 已失效而无法再被访问 */
    void deleteDatasource(Long id);

    void deleteDatasourceList(List<Long> ids);

    /** S0 上下文固化用：启用的数据源精简列表 */
    List<AiDatasource> listEnabledDatasources();

    /**
     * 真实 JDBC 连通性检测：执行 pool_validation_sql，回写健康状态并记一条同步日志。
     * 仅使用管理连接（不触碰只读连接），失败不抛异常而以结果对象返回错误摘要。
     */
    AiDatasourceTestRespVO testConnection(Long id);

}
