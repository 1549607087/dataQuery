package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiQueryFeedback;
import cn.iocoder.boot.module.data_query.mapper.AiQueryFeedbackMapper;
import cn.iocoder.boot.module.data_query.service.AiQueryFeedbackService;
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
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.QUERY_FEEDBACK_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 反馈 Service 实现
 *
 * 标准 CRUD：写入显式补齐 tenant_id 与 creator/updater/createTime/updateTime/deleted；
 */
@Service
public class AiQueryFeedbackServiceImpl extends ServiceImpl<AiQueryFeedbackMapper, AiQueryFeedback>
        implements AiQueryFeedbackService {

    @Override
    public AiQueryFeedback getAiQueryFeedback(Long id) {
        if (id == null) {
            return null;
        }
        List<AiQueryFeedback> list = list(new LambdaQueryWrapperX<AiQueryFeedback>()
                .eq(AiQueryFeedback::getId, id)
                .eq(AiQueryFeedback::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiQueryFeedback(AiQueryFeedback entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiQueryFeedback(AiQueryFeedback entity) {
        Assert.notNull(entity, "entity 不能为空");
        requireExists(entity.getId());
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiQueryFeedback(Long id) {
        requireExists(id);
        update(new LambdaUpdateWrapper<AiQueryFeedback>()
                .eq(AiQueryFeedback::getId, id)
                .set(AiQueryFeedback::getDeleted, 1));
    }

    @Override
    public void deleteAiQueryFeedbackList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            requireExists(id);
        }
        update(new LambdaUpdateWrapper<AiQueryFeedback>()
                .in(AiQueryFeedback::getId, ids)
                .set(AiQueryFeedback::getDeleted, 1));
    }

    private AiQueryFeedback requireExists(Long id) {
        AiQueryFeedback exists = getAiQueryFeedback(id);
        if (exists == null) {
            throw exception(QUERY_FEEDBACK_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；运行时表全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiQueryFeedback entity) {
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

    private void fillUpdateFields(AiQueryFeedback entity) {
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
