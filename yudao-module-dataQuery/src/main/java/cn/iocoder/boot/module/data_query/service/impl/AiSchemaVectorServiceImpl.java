package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiSchemaVector;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.mapper.AiSchemaVectorMapper;
import cn.iocoder.boot.module.data_query.service.AiSchemaVectorService;
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
import java.util.List;
import java.util.Objects;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_OWNERSHIP_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_VECTOR_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Schema 向量索引 Service 实现
 *
 * 与数据源 6 张表同口径：不登记 ignore-tables、不加 @TenantIgnore，
 * 查询靠 TenantDatabaseInterceptor 追加 tenant_id，写入显式 setTenantId 并校验归属。
 */
@Service
public class AiSchemaVectorServiceImpl extends ServiceImpl<AiSchemaVectorMapper, AiSchemaVector>
        implements AiSchemaVectorService {

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Override
    public AiSchemaVector getVector(Long id) {
        if (id == null) {
            return null;
        }
        List<AiSchemaVector> list = list(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getId, id)
                .eq(AiSchemaVector::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AiSchemaVector> listByDatasource(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getDeleted, 0));
    }

    @Override
    public long countByDatasource(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return count(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getDeleted, 0));
    }

    @Override
    public Long createVector(AiSchemaVector entity) {
        Assert.notNull(entity, "entity 不能为空");
        ownerValidator.requireOwned(entity.getDatasourceId());
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateVector(AiSchemaVector entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiSchemaVector exists = requireExists(entity.getId());
        Long targetDatasourceId = entity.getDatasourceId() == null
                ? exists.getDatasourceId() : entity.getDatasourceId();
        if (!Objects.equals(targetDatasourceId, exists.getDatasourceId())) {
            throw exception(DATASOURCE_OWNERSHIP_MISMATCH);
        }
        ownerValidator.requireOwned(targetDatasourceId);
        // MP 默认策略不更新 null 字段。当文本已变更但 Embedding 不可用时，
        // 必须显式清空旧向量，否则会出现「新 content 配旧向量」的静默错配。
        if (entity.getEmbedding() == null && exists.getEmbedding() != null) {
            baseMapper.update(null, new LambdaUpdateWrapper<AiSchemaVector>()
                    .eq(AiSchemaVector::getId, exists.getId())
                    .setSql("embedding = NULL"));
        }
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteVector(Long id) {
        AiSchemaVector exists = requireExists(id);
        ownerValidator.requireOwned(exists.getDatasourceId());
        update(new LambdaUpdateWrapper<AiSchemaVector>()
                .eq(AiSchemaVector::getId, id)
                .set(AiSchemaVector::getDeleted, 1)
                .set(AiSchemaVector::getSyncStatus, 4));
    }

    @Override
    public int retireVectors(Long datasourceId, Collection<Long> keepIds) {
        ownerValidator.requireOwned(datasourceId);
        LambdaUpdateWrapper<AiSchemaVector> wrapper = new LambdaUpdateWrapper<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getDeleted, 0);
        if (keepIds != null && !keepIds.isEmpty()) {
            wrapper.notIn(AiSchemaVector::getId, keepIds);
        }
        wrapper.set(AiSchemaVector::getDeleted, 1)
                .set(AiSchemaVector::getSyncStatus, 4)
                .set(AiSchemaVector::getUpdateTime, LocalDateTime.now());
        // IService#update(Wrapper) 只返回 boolean；这里需要真实行数给构建任务做统计
        return baseMapper.update(null, wrapper);
    }

    private AiSchemaVector requireExists(Long id) {
        AiSchemaVector exists = getVector(id);
        if (exists == null) {
            throw exception(SCHEMA_VECTOR_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；向量表全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiSchemaVector entity) {
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

    private void fillUpdateFields(AiSchemaVector entity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        entity.setTenantId(tenantId);
        entity.setUpdater(userId == null ? "" : userId.toString());
        entity.setUpdateTime(LocalDateTime.now());
        // 禁止前端 / 调用方借更新接口篡改租户或删除标记
        entity.setDeleted(null);
        entity.setCreator(null);
        entity.setCreateTime(null);
    }

}
