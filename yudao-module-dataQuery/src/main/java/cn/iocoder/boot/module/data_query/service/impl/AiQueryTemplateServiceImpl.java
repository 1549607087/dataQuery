package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiQueryTemplate;
import cn.iocoder.boot.module.data_query.mapper.AiQueryTemplateMapper;
import cn.iocoder.boot.module.data_query.service.AiQueryTemplateService;
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
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.QUERY_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 查询模板 Service 实现
 *
 * 标准 CRUD：写入显式补齐 tenant_id 与 creator/updater/createTime/updateTime/deleted；
 */
@Service
public class AiQueryTemplateServiceImpl extends ServiceImpl<AiQueryTemplateMapper, AiQueryTemplate>
        implements AiQueryTemplateService {

    @Override
    public AiQueryTemplate getAiQueryTemplate(Long id) {
        if (id == null) {
            return null;
        }
        List<AiQueryTemplate> list = list(new LambdaQueryWrapperX<AiQueryTemplate>()
                .eq(AiQueryTemplate::getId, id)
                .eq(AiQueryTemplate::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiQueryTemplate(AiQueryTemplate entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiQueryTemplate(AiQueryTemplate entity) {
        Assert.notNull(entity, "entity 不能为空");
        requireExists(entity.getId());
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiQueryTemplate(Long id) {
        requireExists(id);
        update(new LambdaUpdateWrapper<AiQueryTemplate>()
                .eq(AiQueryTemplate::getId, id)
                .set(AiQueryTemplate::getDeleted, 1));
    }

    @Override
    public void deleteAiQueryTemplateList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            requireExists(id);
        }
        update(new LambdaUpdateWrapper<AiQueryTemplate>()
                .in(AiQueryTemplate::getId, ids)
                .set(AiQueryTemplate::getDeleted, 1));
    }

    private AiQueryTemplate requireExists(Long id) {
        AiQueryTemplate exists = getAiQueryTemplate(id);
        if (exists == null) {
            throw exception(QUERY_TEMPLATE_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；运行时表全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiQueryTemplate entity) {
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

    private void fillUpdateFields(AiQueryTemplate entity) {
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
