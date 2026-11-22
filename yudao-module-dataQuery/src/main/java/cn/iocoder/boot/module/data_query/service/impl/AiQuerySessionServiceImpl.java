package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.mapper.AiQuerySessionMapper;
import cn.iocoder.boot.module.data_query.service.AiQuerySessionService;
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
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.QUERY_SESSION_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;

/**
 * 问数会话 Service 实现
 *
 * 标准 CRUD：写入显式补齐 tenant_id 与 creator/updater/createTime/updateTime/deleted；
 */
@Service
public class AiQuerySessionServiceImpl extends ServiceImpl<AiQuerySessionMapper, AiQuerySession>
        implements AiQuerySessionService {

    @Override
    public AiQuerySession getAiQuerySession(Long id) {
        if (id == null) {
            return null;
        }
        List<AiQuerySession> list = list(new LambdaQueryWrapperX<AiQuerySession>()
                .eq(AiQuerySession::getId, id)
                .eq(AiQuerySession::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiQuerySession(AiQuerySession entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiQuerySession(AiQuerySession entity) {
        Assert.notNull(entity, "entity 不能为空");
        requireExists(entity.getId());
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiQuerySession(Long id) {
        requireExists(id);
        update(new LambdaUpdateWrapper<AiQuerySession>()
                .eq(AiQuerySession::getId, id)
                .set(AiQuerySession::getDeleted, 1));
    }

    @Override
    public void deleteAiQuerySessionList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            requireExists(id);
        }
        update(new LambdaUpdateWrapper<AiQuerySession>()
                .in(AiQuerySession::getId, ids)
                .set(AiQuerySession::getDeleted, 1));
    }

    @Override
    public void advanceLeaf(Long sessionId, Long leafId) {
        requireExists(sessionId);
        // 定点更新：只推进叶子指针与提问时间，不累加 message_count、不改标题/状态。
        // 租户隔离由 TenantDatabaseInterceptor 对 UPDATE 自动追加 tenant_id 保证。
        update(new LambdaUpdateWrapper<AiQuerySession>()
                .eq(AiQuerySession::getId, sessionId)
                .eq(AiQuerySession::getDeleted, 0)
                .set(AiQuerySession::getCurrentLeafId, leafId)
                .set(AiQuerySession::getLastAskTime, LocalDateTime.now())
                .set(AiQuerySession::getUpdateTime, LocalDateTime.now()));
    }

    private AiQuerySession requireExists(Long id) {
        AiQuerySession exists = getAiQuerySession(id);
        if (exists == null) {
            throw exception(QUERY_SESSION_NOT_EXISTS);
        }
        return exists;
    }

    /** 实体是普通 POJO，框架不会自动填充；运行时表全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiQuerySession entity) {
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

    private void fillUpdateFields(AiQuerySession entity) {
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
