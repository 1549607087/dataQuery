package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceColumnMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceColumnService;
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
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_COLUMN_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 数据源字段元数据 Service 实现
 */
@Service
public class AiDatasourceColumnServiceImpl extends ServiceImpl<AiDatasourceColumnMapper, AiDatasourceColumn>
        implements AiDatasourceColumnService {

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Resource
    private SchemaVectorIndexSyncService schemaVectorIndexSyncService;

    @Override
    public AiDatasourceColumn getAiDatasourceColumn(Long id) {
        if (id == null) {
            return null;
        }
        List<AiDatasourceColumn> list = list(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getId, id)
                .eq(AiDatasourceColumn::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiDatasourceColumn(AiDatasourceColumn entity) {
        Assert.notNull(entity, "entity 不能为空");
        ownerValidator.requireOwned(entity.getDatasourceId());
        fillCreateFields(entity);
        save(entity);
        // 新字段还没有向量行，且所属表 Document 的「包含字段」也会变，直接触发增量构建
        schemaVectorIndexSyncService.onStructureChanged(entity.getDatasourceId());
        return entity.getId();
    }

    @Override
    public void updateAiDatasourceColumn(AiDatasourceColumn entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiDatasourceColumn exists = requireExists(entity.getId());
        Long targetDatasourceId = entity.getDatasourceId() == null
                ? exists.getDatasourceId() : entity.getDatasourceId();
        if (!Objects.equals(targetDatasourceId, exists.getDatasourceId())) {
            throw exception(DATASOURCE_OWNERSHIP_MISMATCH);
        }
        ownerValidator.requireOwned(targetDatasourceId);
        fillUpdateFields(entity);
        updateById(entity);
        // 中文名 / 描述 / 指标口径 / 同义词 / 单位 / 枚举含义都在该列的 Document 正文里
        schemaVectorIndexSyncService.onColumnsChanged(targetDatasourceId, Collections.singletonList(entity.getId()));
    }

    @Override
    public void deleteAiDatasourceColumn(Long id) {
        AiDatasourceColumn exists = requireExists(id);
        ownerValidator.requireOwned(exists.getDatasourceId());
        update(new LambdaUpdateWrapper<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getId, id)
                .set(AiDatasourceColumn::getDeleted, 1));
        schemaVectorIndexSyncService.onStructureChanged(exists.getDatasourceId());
    }

    @Override
    public void deleteAiDatasourceColumnList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> datasourceIds = new LinkedHashSet<>();
        for (Long id : ids) {
            AiDatasourceColumn exists = requireExists(id);
            ownerValidator.requireOwned(exists.getDatasourceId());
            datasourceIds.add(exists.getDatasourceId());
        }
        update(new LambdaUpdateWrapper<AiDatasourceColumn>()
                .in(AiDatasourceColumn::getId, ids)
                .set(AiDatasourceColumn::getDeleted, 1));
        datasourceIds.forEach(schemaVectorIndexSyncService::onStructureChanged);
    }

    @Override
    public List<AiDatasourceColumn> listByDatasourceId(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                .eq(AiDatasourceColumn::getDeleted, 0)
                .orderByAsc(AiDatasourceColumn::getSort)
                .orderByDesc(AiDatasourceColumn::getId)
        );
    }

    @Override
    public List<AiDatasourceColumn> listVisibleColumns(Long tableId) {
        return list(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getTableId, tableId)
                .eq(AiDatasourceColumn::getVisible, 1)
                .eq(AiDatasourceColumn::getQueryable, 1)
                .ne(AiDatasourceColumn::getSyncStatus, 4)
                .eq(AiDatasourceColumn::getDeleted, 0)
                .orderByAsc(AiDatasourceColumn::getSort)
                .orderByAsc(AiDatasourceColumn::getOrdinalPosition));
    }

    @Override
    public List<AiDatasourceColumn> listVisibleColumnsByDatasource(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                .eq(AiDatasourceColumn::getVisible, 1)
                .eq(AiDatasourceColumn::getQueryable, 1)
                .ne(AiDatasourceColumn::getSyncStatus, 4)
                .eq(AiDatasourceColumn::getDeleted, 0)
                .orderByAsc(AiDatasourceColumn::getTableId)
                .orderByAsc(AiDatasourceColumn::getOrdinalPosition));
    }

    private AiDatasourceColumn requireExists(Long id) {
        AiDatasourceColumn exists = getAiDatasourceColumn(id);
        if (exists == null) {
            throw exception(DATASOURCE_COLUMN_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；数据源侧全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiDatasourceColumn entity) {
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

    private void fillUpdateFields(AiDatasourceColumn entity) {
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
