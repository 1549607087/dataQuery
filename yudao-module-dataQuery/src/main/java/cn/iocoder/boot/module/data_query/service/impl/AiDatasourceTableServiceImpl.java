package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceTableMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableService;
import cn.iocoder.boot.module.data_query.service.SchemaVectorIndexSyncService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_OWNERSHIP_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_TABLE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 数据源表元数据 Service 实现
 */
@Service
public class AiDatasourceTableServiceImpl extends ServiceImpl<AiDatasourceTableMapper, AiDatasourceTable>
        implements AiDatasourceTableService {

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Resource
    private SchemaVectorIndexSyncService schemaVectorIndexSyncService;

    @Override
    public AiDatasourceTable getAiDatasourceTable(Long id) {
        if (id == null) {
            return null;
        }
        List<AiDatasourceTable> list = list(new LambdaQueryWrapperX<AiDatasourceTable>()
                .eq(AiDatasourceTable::getId, id)
                .eq(AiDatasourceTable::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiDatasourceTable(AiDatasourceTable entity) {
        Assert.notNull(entity, "entity 不能为空");
        ownerValidator.requireOwned(entity.getDatasourceId());
        fillCreateFields(entity);
        save(entity);
        // 新表在向量表里还没有对应行，标脏更新不到任何记录，只能触发一次构建把它建出来
        schemaVectorIndexSyncService.onStructureChanged(entity.getDatasourceId());
        return entity.getId();
    }

    @Override
    public void updateAiDatasourceTable(AiDatasourceTable entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiDatasourceTable exists = requireExists(entity.getId());
        Long targetDatasourceId = entity.getDatasourceId() == null
                ? exists.getDatasourceId() : entity.getDatasourceId();
        if (!Objects.equals(targetDatasourceId, exists.getDatasourceId())) {
            throw exception(DATASOURCE_OWNERSHIP_MISMATCH);
        }
        ownerValidator.requireOwned(targetDatasourceId);
        fillUpdateFields(entity);
        updateById(entity);
        // 名称 / 中文名 / 业务说明 / 默认时间字段都在向量 Document 正文里，改完必须重算这一行
        schemaVectorIndexSyncService.onTablesChanged(targetDatasourceId, Collections.singletonList(entity.getId()));
    }

    @Override
    public void deleteAiDatasourceTable(Long id) {
        AiDatasourceTable exists = requireExists(id);
        ownerValidator.requireOwned(exists.getDatasourceId());
        update(new LambdaUpdateWrapper<AiDatasourceTable>()
                .eq(AiDatasourceTable::getId, id)
                .set(AiDatasourceTable::getDeleted, 1));
        // 该表与其列的向量行由构建侧 retireVectors 下线，这里只需触发一次增量构建
        schemaVectorIndexSyncService.onStructureChanged(exists.getDatasourceId());
    }

    @Override
    public void deleteAiDatasourceTableList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> datasourceIds = new LinkedHashSet<>();
        for (Long id : ids) {
            AiDatasourceTable exists = requireExists(id);
            ownerValidator.requireOwned(exists.getDatasourceId());
            datasourceIds.add(exists.getDatasourceId());
        }
        update(new LambdaUpdateWrapper<AiDatasourceTable>()
                .in(AiDatasourceTable::getId, ids)
                .set(AiDatasourceTable::getDeleted, 1));
        datasourceIds.forEach(schemaVectorIndexSyncService::onStructureChanged);
    }

    @Override
    public List<AiDatasourceTable> listByDatasourceId(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceTable>()
                .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                .eq(AiDatasourceTable::getDeleted, 0)
                .orderByAsc(AiDatasourceTable::getSort)
                .orderByDesc(AiDatasourceTable::getId)
        );
    }

    @Override
    public List<AiDatasourceTable> listVisibleTables(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceTable>()
                .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                .eq(AiDatasourceTable::getVisible, 1)
                .eq(AiDatasourceTable::getQueryable, 1)
                .ne(AiDatasourceTable::getSyncStatus, 4)
                .eq(AiDatasourceTable::getDeleted, 0)
                .orderByAsc(AiDatasourceTable::getSort)
                .orderByAsc(AiDatasourceTable::getId));
    }

    private AiDatasourceTable requireExists(Long id) {
        AiDatasourceTable exists = getAiDatasourceTable(id);
        if (exists == null) {
            throw exception(DATASOURCE_TABLE_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；数据源侧全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiDatasourceTable entity) {
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

    private void fillUpdateFields(AiDatasourceTable entity) {
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
