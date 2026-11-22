package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiQueryAudit;
import cn.iocoder.boot.module.data_query.mapper.AiQueryAuditMapper;
import cn.iocoder.boot.module.data_query.service.AiQueryAuditService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.QUERY_AUDIT_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 查询审计 Service 实现
 *
 * append-only：管理端 Controller 仅暴露 query / export，Service 层写入供 P4 运行时留痕使用；
 */
@Service
public class AiQueryAuditServiceImpl extends ServiceImpl<AiQueryAuditMapper, AiQueryAudit>
        implements AiQueryAuditService {

    @Override
    public AiQueryAudit getAiQueryAudit(Long id) {
        if (id == null) {
            return null;
        }
        List<AiQueryAudit> list = list(new LambdaQueryWrapperX<AiQueryAudit>()
                .eq(AiQueryAudit::getId, id)
                .eq(AiQueryAudit::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiQueryAudit(AiQueryAudit entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiQueryAudit(AiQueryAudit entity) {
        Assert.notNull(entity, "entity 不能为空");
        requireExists(entity.getId());
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiQueryAudit(Long id) {
        requireExists(id);
        update(new LambdaUpdateWrapper<AiQueryAudit>()
                .eq(AiQueryAudit::getId, id)
                .set(AiQueryAudit::getDeleted, 1));
    }

    @Override
    public void deleteAiQueryAuditList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            requireExists(id);
        }
        update(new LambdaUpdateWrapper<AiQueryAudit>()
                .in(AiQueryAudit::getId, ids)
                .set(AiQueryAudit::getDeleted, 1));
    }

    private AiQueryAudit requireExists(Long id) {
        AiQueryAudit exists = getAiQueryAudit(id);
        if (exists == null) {
            throw exception(QUERY_AUDIT_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；运行时表全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiQueryAudit entity) {
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

    private void fillUpdateFields(AiQueryAudit entity) {
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
