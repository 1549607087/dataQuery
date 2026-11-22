package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumnEnum;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * 字段枚举映射 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId，并按 datasource_id 校验归属（AiDatasourceOwnerValidator）。
 * 严禁使用 IRepository#getOne(Wrapper)：MP 3.5.17 走 selectCursor，与 Druid PSCache 冲突。
 */
public interface AiDatasourceColumnEnumService extends IService<AiDatasourceColumnEnum> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiDatasourceColumnEnum getAiDatasourceColumnEnum(Long id);

    Long createAiDatasourceColumnEnum(AiDatasourceColumnEnum entity);

    void updateAiDatasourceColumnEnum(AiDatasourceColumnEnum entity);

    void deleteAiDatasourceColumnEnum(Long id);

    void deleteAiDatasourceColumnEnumList(List<Long> ids);

    /** 按数据源查询（先校验数据源归属，再查本表） */
    List<AiDatasourceColumnEnum> listByDatasourceId(Long datasourceId);

    /** 提示词注入：取某列启用的枚举映射 */
    List<AiDatasourceColumnEnum> listEnabledByColumnId(Long columnId);

}
