package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceSyncLog;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceSyncLogMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceSyncLogService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_OWNERSHIP_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_SYNC_LOG_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 数据源同步日志 Service 实现
 */
@Service
public class AiDatasourceSyncLogServiceImpl extends ServiceImpl<AiDatasourceSyncLogMapper, AiDatasourceSyncLog>
        implements AiDatasourceSyncLogService {

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Override
    public AiDatasourceSyncLog getAiDatasourceSyncLog(Long id) {
        if (id == null) {
            return null;
        }
        List<AiDatasourceSyncLog> list = list(new LambdaQueryWrapperX<AiDatasourceSyncLog>()
                .eq(AiDatasourceSyncLog::getId, id)
                .eq(AiDatasourceSyncLog::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiDatasourceSyncLog(AiDatasourceSyncLog entity) {
        Assert.notNull(entity, "entity 不能为空");
        ownerValidator.requireOwned(entity.getDatasourceId());
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiDatasourceSyncLog(AiDatasourceSyncLog entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiDatasourceSyncLog exists = requireExists(entity.getId());
        Long targetDatasourceId = entity.getDatasourceId() == null
                ? exists.getDatasourceId() : entity.getDatasourceId();
        if (!Objects.equals(targetDatasourceId, exists.getDatasourceId())) {
            throw exception(DATASOURCE_OWNERSHIP_MISMATCH);
        }
        ownerValidator.requireOwned(targetDatasourceId);
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiDatasourceSyncLog(Long id) {
        AiDatasourceSyncLog exists = requireExists(id);
        ownerValidator.requireOwned(exists.getDatasourceId());
        update(new LambdaUpdateWrapper<AiDatasourceSyncLog>()
                .eq(AiDatasourceSyncLog::getId, id)
                .set(AiDatasourceSyncLog::getDeleted, 1));
    }

    @Override
    public void deleteAiDatasourceSyncLogList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            AiDatasourceSyncLog exists = requireExists(id);
            ownerValidator.requireOwned(exists.getDatasourceId());
        }
        update(new LambdaUpdateWrapper<AiDatasourceSyncLog>()
                .in(AiDatasourceSyncLog::getId, ids)
                .set(AiDatasourceSyncLog::getDeleted, 1));
    }

    @Override
    public List<AiDatasourceSyncLog> listByDatasourceId(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceSyncLog>()
                .eq(AiDatasourceSyncLog::getDatasourceId, datasourceId)
                .eq(AiDatasourceSyncLog::getDeleted, 0)
                .orderByDesc(AiDatasourceSyncLog::getId)
        );
    }

    @Override
    public Long createLog(AiDatasourceSyncLog log) {
        ownerValidator.requireOwned(log.getDatasourceId());
        fillCreateFields(log);
        save(log);
        return log.getId();
    }

    private AiDatasourceSyncLog requireExists(Long id) {
        AiDatasourceSyncLog exists = getAiDatasourceSyncLog(id);
        if (exists == null) {
            throw exception(DATASOURCE_SYNC_LOG_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；数据源侧全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiDatasourceSyncLog entity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String user = userId == null ? "" : userId.toString();
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 ASSIGN_ID 生成
        entity.setTenantId(tenantId);
        entity.setCreator(user);
        entity.setUpdater(user);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void fillUpdateFields(AiDatasourceSyncLog entity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        entity.setTenantId(tenantId);
        entity.setUpdater(userId == null ? "" : userId.toString());
        entity.setUpdateTime(LocalDateTime.now());
    }
}
