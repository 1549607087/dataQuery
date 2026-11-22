package cn.iocoder.boot.module.data_query.framework.datasource;

import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_ID_REQUIRED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 数据源归属校验器：所有元数据子表写入前都必须调用。
 *
 * 为什么单独抽成组件而不是注入 AiDatasourceService：
 * 主数据源 Service 会写同步日志（依赖 SyncLogService），而 SyncLogService 又要校验归属。
 * 若互相注入 Service 会形成循环依赖；这里只依赖 Mapper，从结构上切断环。
 *
 * 三道边界：
 * 1. TenantDatabaseInterceptor 自动给 SELECT 追加 tenant_id = 当前租户；
 * 2. 这里显式比对查出的 tenant_id 与当前上下文（拦截器被误关时仍拦得住）；
 * 3. 写入方显式 setTenantId（见各 ServiceImpl#fillCreateFields）。
 *
 * 数据源 6 张表严禁登记 yudao.tenant.ignore-tables，也不加 @TenantIgnore。
 */
@Component
public class AiDatasourceOwnerValidator {

    @Resource
    private AiDatasourceMapper datasourceMapper;

    /**
     * 校验数据源存在且属于当前租户，返回数据源实体。
     *
     * @param datasourceId 数据源编号
     */
    public AiDatasource requireOwned(Long datasourceId) {
        if (datasourceId == null) {
            throw exception(DATASOURCE_ID_REQUIRED);
        }
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        // 不用 IRepository#getOne：MP 3.5.17 走 selectCursor，与 Druid PSCache 组合会复用已关闭的 Statement
        List<AiDatasource> list = datasourceMapper.selectList(new LambdaQueryWrapperX<AiDatasource>()
                .eq(AiDatasource::getId, datasourceId)
                .eq(AiDatasource::getDeleted, 0)
                .last("LIMIT 1"));
        if (list.isEmpty()) {
            throw exception(DATASOURCE_NOT_EXISTS);
        }
        AiDatasource datasource = list.get(0);
        if (datasource.getTenantId() == null || !currentTenantId.equals(datasource.getTenantId())) {
            // 理论上不可达：拦截器已按 tenant_id 过滤。保留为纵深防御，并统一用「不存在」口径避免信息泄露
            throw exception(DATASOURCE_NOT_EXISTS);
        }
        return datasource;
    }

}
