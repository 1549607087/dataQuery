package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceSyncLog;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * 数据源同步日志 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId，并按 datasource_id 校验归属（AiDatasourceOwnerValidator）。
 * 严禁使用 IRepository#getOne(Wrapper)：MP 3.5.17 走 selectCursor，与 Druid PSCache 冲突。
 */
public interface AiDatasourceSyncLogService extends IService<AiDatasourceSyncLog> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiDatasourceSyncLog getAiDatasourceSyncLog(Long id);

    Long createAiDatasourceSyncLog(AiDatasourceSyncLog entity);

    void updateAiDatasourceSyncLog(AiDatasourceSyncLog entity);

    void deleteAiDatasourceSyncLog(Long id);

    void deleteAiDatasourceSyncLogList(List<Long> ids);

    /** 按数据源查询（先校验数据源归属，再查本表） */
    List<AiDatasourceSyncLog> listByDatasourceId(Long datasourceId);

    /** 供同步 / 连通性任务写入日志；会校验 datasource_id 归属并显式补租户号 */
    Long createLog(AiDatasourceSyncLog log);

}
