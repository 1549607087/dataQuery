package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import cn.iocoder.boot.module.data_query.mapper.AiQueryMessageMapper;
import cn.iocoder.boot.module.data_query.service.AiQueryMessageService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 问数对话消息 Service 实现
 *
 * 写入显式补齐 tenant_id / 审计字段；查询依赖租户拦截器，全部租户私有。
 */
@Service
public class AiQueryMessageServiceImpl extends ServiceImpl<AiQueryMessageMapper, AiQueryMessage>
        implements AiQueryMessageService {

    @Override
    public AiQueryMessage getAiQueryMessage(Long id) {
        if (id == null) {
            return null;
        }
        List<AiQueryMessage> list = list(new LambdaQueryWrapperX<AiQueryMessage>()
                .eq(AiQueryMessage::getId, id)
                .eq(AiQueryMessage::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createAiQueryMessage(AiQueryMessage entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateAiQueryMessage(AiQueryMessage entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteAiQueryMessage(Long id) {
        update(new LambdaUpdateWrapper<AiQueryMessage>()
                .eq(AiQueryMessage::getId, id)
                .set(AiQueryMessage::getDeleted, 1)
                .set(AiQueryMessage::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public List<AiQueryMessage> listActiveBySession(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapperX<AiQueryMessage>()
                .eq(AiQueryMessage::getSessionId, sessionId)
                .eq(AiQueryMessage::getIsActive, 1)
                .eq(AiQueryMessage::getDeleted, 0)
                .orderByAsc(AiQueryMessage::getSeq)
                .orderByAsc(AiQueryMessage::getId));
    }

    @Override
    public List<AiQueryMessage> listByParent(Long parentId) {
        if (parentId == null) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapperX<AiQueryMessage>()
                .eq(AiQueryMessage::getParentId, parentId)
                .eq(AiQueryMessage::getDeleted, 0)
                .orderByAsc(AiQueryMessage::getVersion)
                .orderByAsc(AiQueryMessage::getId));
    }

    @Override
    public List<AiQueryMessage> listBySession(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapperX<AiQueryMessage>()
                .eq(AiQueryMessage::getSessionId, sessionId)
                .eq(AiQueryMessage::getDeleted, 0)
                .orderByAsc(AiQueryMessage::getSeq)
                .orderByAsc(AiQueryMessage::getId));
    }

    @Override
    public void deactivate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        update(new LambdaUpdateWrapper<AiQueryMessage>()
                .in(AiQueryMessage::getId, ids)
                .set(AiQueryMessage::getIsActive, 0)
                .set(AiQueryMessage::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void deactivateFromSeq(Long sessionId, int fromSeq) {
        if (sessionId == null) {
            return;
        }
        // 带 deleted=0 / is_active=1 条件，避免误伤历史版本与已删消息；
        // 只改 is_active 与 update_time，不物理删除，保留分支可追溯。
        update(new LambdaUpdateWrapper<AiQueryMessage>()
                .eq(AiQueryMessage::getSessionId, sessionId)
                .eq(AiQueryMessage::getIsActive, 1)
                .eq(AiQueryMessage::getDeleted, 0)
                .ge(AiQueryMessage::getSeq, fromSeq)
                .set(AiQueryMessage::getIsActive, 0)
                .set(AiQueryMessage::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQuestionAndAnswer(AiQueryMessage question, AiQueryMessage answer) {
        createAiQueryMessage(question);
        createAiQueryMessage(answer);
        return answer.getId();
    }

    // ==================== 对话树模型（阶段 1）====================

    @Override
    public AiQueryMessage lockMessage(Long messageId) {
        return baseMapper.lockMessage(requireTenantId(), messageId);
    }

    @Override
    public List<AiQueryMessage> findActivePath(Long sessionId, Long leafId) {
        if (sessionId == null || leafId == null) {
            return Collections.emptyList();
        }
        return baseMapper.findActivePath(requireTenantId(), sessionId, leafId);
    }

    @Override
    public Long findActiveLeaf(Long sessionId, Long targetId) {
        if (sessionId == null || targetId == null) {
            return null;
        }
        return baseMapper.findActiveLeaf(requireTenantId(), sessionId, targetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchSiblingActive(Long sessionId, Long parentId, String role, Integer seq, Long targetId) {
        Long tenantId = requireTenantId();
        baseMapper.deactivateSiblings(tenantId, sessionId, parentId, role, seq, targetId);
        baseMapper.activateSibling(tenantId, sessionId, targetId);
    }

    /** 原生 SQL 的 tenant_id 只从上下文取，绝不接受调用方传参 */
    private static Long requireTenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private void fillCreateFields(AiQueryMessage entity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String user = userId == null ? "" : userId.toString();
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null);
        entity.setTenantId(tenantId);
        if (entity.getUserId() == null) {
            entity.setUserId(userId);
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(1);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        if (entity.getRating() == null) {
            entity.setRating(0);
        }
        entity.setCreator(user);
        entity.setUpdater(user);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void fillUpdateFields(AiQueryMessage entity) {
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
