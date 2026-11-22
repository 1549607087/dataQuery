package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumnEnum;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceColumnEnumMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceColumnEnumService;
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
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_COLUMN_ENUM_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 字段枚举映射 Service 实现
 */
@Service
public class AiDatasourceColumnEnumServiceImpl extends ServiceImpl<AiDatasourceColumnEnumMapper, AiDatasourceColumnEnum>
        implements AiDatasourceColumnEnumService {

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Override
    public AiDatasourceColumnEnum getAiDatasourceColumnEnum(Long id) {
        if (id == null) {
            return null;
        }
        List<AiDatasourceColumnEnum> list = list(new LambdaQueryWrapperX<AiDatasourceColumnEnum>()
                .eq(AiDatasourceColumnEnum::getId, id)
                .eq(AiDatasourceColumnEnum::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiDatasourceColumnEnum(AiDatasourceColumnEnum entity) {
        Assert.notNull(entity, "entity 不能为空");
        ownerValidator.requireOwned(entity.getDatasourceId());
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiDatasourceColumnEnum(AiDatasourceColumnEnum entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiDatasourceColumnEnum exists = requireExists(entity.getId());
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
    public void deleteAiDatasourceColumnEnum(Long id) {
        AiDatasourceColumnEnum exists = requireExists(id);
        ownerValidator.requireOwned(exists.getDatasourceId());
        update(new LambdaUpdateWrapper<AiDatasourceColumnEnum>()
                .eq(AiDatasourceColumnEnum::getId, id)
                .set(AiDatasourceColumnEnum::getDeleted, 1));
    }

    @Override
    public void deleteAiDatasourceColumnEnumList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            AiDatasourceColumnEnum exists = requireExists(id);
            ownerValidator.requireOwned(exists.getDatasourceId());
        }
        update(new LambdaUpdateWrapper<AiDatasourceColumnEnum>()
                .in(AiDatasourceColumnEnum::getId, ids)
                .set(AiDatasourceColumnEnum::getDeleted, 1));
    }

    @Override
    public List<AiDatasourceColumnEnum> listByDatasourceId(Long datasourceId) {
        ownerValidator.requireOwned(datasourceId);
        return list(new LambdaQueryWrapperX<AiDatasourceColumnEnum>()
                .eq(AiDatasourceColumnEnum::getDatasourceId, datasourceId)
                .eq(AiDatasourceColumnEnum::getDeleted, 0)
                .orderByAsc(AiDatasourceColumnEnum::getSort)
                .orderByDesc(AiDatasourceColumnEnum::getId)
        );
    }

    @Override
    public List<AiDatasourceColumnEnum> listEnabledByColumnId(Long columnId) {
        return list(new LambdaQueryWrapperX<AiDatasourceColumnEnum>()
                .eq(AiDatasourceColumnEnum::getColumnId, columnId)
                .eq(AiDatasourceColumnEnum::getStatus, 0)
                .eq(AiDatasourceColumnEnum::getDeleted, 0)
                .orderByAsc(AiDatasourceColumnEnum::getSort));
    }

    private AiDatasourceColumnEnum requireExists(Long id) {
        AiDatasourceColumnEnum exists = getAiDatasourceColumnEnum(id);
        if (exists == null) {
            throw exception(DATASOURCE_COLUMN_ENUM_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；数据源侧全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiDatasourceColumnEnum entity) {
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

    private void fillUpdateFields(AiDatasourceColumnEnum entity) {
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
