package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTableRelation;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceTableRelationMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableRelationService;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableService;
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
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_TABLE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_TABLE_RELATION_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 表关系 / Join 路径 Service 实现
 */
@Service
public class AiDatasourceTableRelationServiceImpl extends ServiceImpl<AiDatasourceTableRelationMapper, AiDatasourceTableRelation>
        implements AiDatasourceTableRelationService {

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Resource
    private AiDatasourceTableService aiDatasourceTableService;

    @Override
    public AiDatasourceTableRelation getAiDatasourceTableRelation(Long id) {
        if (id == null) {
            return null;
        }
        List<AiDatasourceTableRelation> list = list(new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                .eq(AiDatasourceTableRelation::getId, id)
                .eq(AiDatasourceTableRelation::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiDatasourceTableRelation(AiDatasourceTableRelation entity) {
        Assert.notNull(entity, "entity 不能为空");
        ownerValidator.requireOwned(entity.getDatasourceId());
        // 表物理名一律服务端按 leftTableId/rightTableId 解析，不接受前端传入
        resolveAndFillTableNames(entity);
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiDatasourceTableRelation(AiDatasourceTableRelation entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiDatasourceTableRelation exists = requireExists(entity.getId());
        Long targetDatasourceId = entity.getDatasourceId() == null
                ? exists.getDatasourceId() : entity.getDatasourceId();
        if (!Objects.equals(targetDatasourceId, exists.getDatasourceId())) {
            throw exception(DATASOURCE_OWNERSHIP_MISMATCH);
        }
        ownerValidator.requireOwned(targetDatasourceId);
        entity.setDatasourceId(targetDatasourceId);
        // 表物理名一律服务端按 leftTableId/rightTableId 解析，不接受前端传入
        resolveAndFillTableNames(entity);
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiDatasourceTableRelation(Long id) {
        AiDatasourceTableRelation exists = requireExists(id);
        ownerValidator.requireOwned(exists.getDatasourceId());
        update(new LambdaUpdateWrapper<AiDatasourceTableRelation>()
                .eq(AiDatasourceTableRelation::getId, id)
                .set(AiDatasourceTableRelation::getDeleted, 1));
    }

    @Override
    public void deleteAiDatasourceTableRelationList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            AiDatasourceTableRelation exists = requireExists(id);
            ownerValidator.requireOwned(exists.getDatasourceId());
        }
        update(new LambdaUpdateWrapper<AiDatasourceTableRelation>()
                .in(AiDatasourceTableRelation::getId, ids)
                .set(AiDatasourceTableRelation::getDeleted, 1));
    }

    @Override
    public List<AiDatasourceTableRelation> listByDatasourceId(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                .eq(AiDatasourceTableRelation::getDatasourceId, datasourceId)
                .eq(AiDatasourceTableRelation::getDeleted, 0)
                .orderByAsc(AiDatasourceTableRelation::getSort)
                .orderByDesc(AiDatasourceTableRelation::getId)
        );
    }

    @Override
    public List<AiDatasourceTableRelation> listVerifiedRelations(Long datasourceId, Collection<Long> tableIds) {
        ownerValidator.requireOwned(datasourceId);
        if (tableIds == null || tableIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                .eq(AiDatasourceTableRelation::getDatasourceId, datasourceId)
                .eq(AiDatasourceTableRelation::getVerified, 1)
                .eq(AiDatasourceTableRelation::getEnabled, 1)
                .eq(AiDatasourceTableRelation::getStatus, 0)
                .in(AiDatasourceTableRelation::getLeftTableId, tableIds)
                .in(AiDatasourceTableRelation::getRightTableId, tableIds)
                .eq(AiDatasourceTableRelation::getDeleted, 0)
                .orderByAsc(AiDatasourceTableRelation::getCost));
    }

    @Override
    public List<AiDatasourceTableRelation> listVerifiedRelationsForExpand(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                .eq(AiDatasourceTableRelation::getDatasourceId, datasourceId)
                .eq(AiDatasourceTableRelation::getVerified, 1)
                .eq(AiDatasourceTableRelation::getEnabled, 1)
                .eq(AiDatasourceTableRelation::getStatus, 0)
                .eq(AiDatasourceTableRelation::getDeleted, 0)
                .orderByAsc(AiDatasourceTableRelation::getCost));
    }

    private AiDatasourceTableRelation requireExists(Long id) {
        AiDatasourceTableRelation exists = getAiDatasourceTableRelation(id);
        if (exists == null) {
            throw exception(DATASOURCE_TABLE_RELATION_NOT_EXISTS);
        }
        return exists;
    }

    /**
     * 按 leftTableId/rightTableId 从元数据反查物理名并覆盖实体字段。
     *
     * <p>前端只负责选表（ID），不负责写物理名；getAiDatasourceTable 已按当前租户 +
     * deleted=0 过滤，这里再校验两张表必须属于同一数据源，避免关系跨数据源。
     */
    private void resolveAndFillTableNames(AiDatasourceTableRelation entity) {
        AiDatasourceTable left = requireTable(entity.getLeftTableId());
        AiDatasourceTable right = requireTable(entity.getRightTableId());
        if (!Objects.equals(left.getDatasourceId(), entity.getDatasourceId())
                || !Objects.equals(right.getDatasourceId(), entity.getDatasourceId())) {
            throw exception(DATASOURCE_OWNERSHIP_MISMATCH);
        }
        entity.setLeftTableName(left.getTableName());
        entity.setRightTableName(right.getTableName());
    }

    private AiDatasourceTable requireTable(Long tableId) {
        AiDatasourceTable table = aiDatasourceTableService.getAiDatasourceTable(tableId);
        if (table == null) {
            throw exception(DATASOURCE_TABLE_NOT_EXISTS);
        }
        return table;
    }

    /** 实体是普通 POJO，框架不会自动填充；数据源侧全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiDatasourceTableRelation entity) {
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

    private void fillUpdateFields(AiDatasourceTableRelation entity) {
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
