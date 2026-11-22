package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.mapper.KnowledgeMessageMapper;
import cn.iocoder.boot.module.data_query.service.KnowledgeMessageService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库问答消息 Service 实现
 *
 * <p>对话树原生 SQL 的 tenantId 由本层从 TenantContextHolder 取后透传，绝不接受前端传参。
 */
@Service
public class KnowledgeMessageServiceImpl extends ServiceImpl<KnowledgeMessageMapper, AiKnowledgeMessage>
        implements KnowledgeMessageService {

    @Override
    public AiKnowledgeMessage getKnowledgeMessage(Long id) {
        if (id == null) {
            return null;
        }
        List<AiKnowledgeMessage> list = list(new LambdaQueryWrapperX<AiKnowledgeMessage>()
                .eq(AiKnowledgeMessage::getId, id)
                .eq(AiKnowledgeMessage::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long createKnowledgeMessage(AiKnowledgeMessage entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateKnowledgeMessage(AiKnowledgeMessage entity) {
        Assert.notNull(entity, "entity 不能为空");
        if (getKnowledgeMessage(entity.getId()) == null) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        Long tenantId = requireTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        entity.setTenantId(tenantId);
        entity.setUpdater(userId == null ? "" : userId.toString());
        entity.setUpdateTime(LocalDateTime.now());
        updateById(entity);
    }

    @Override
    public void deleteKnowledgeMessage(Long id) {
        if (getKnowledgeMessage(id) == null) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        update(new LambdaUpdateWrapper<AiKnowledgeMessage>()
                .eq(AiKnowledgeMessage::getId, id)
                .set(AiKnowledgeMessage::getDeleted, 1)
                .set(AiKnowledgeMessage::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void deleteBySession(Long sessionId) {
        // 单条 UPDATE 软删整会话消息，避免逐条循环；tenant_id 由租户插件追加，这里再显式等值一次
        Long tenantId = requireTenantId();
        update(new LambdaUpdateWrapper<AiKnowledgeMessage>()
                .eq(AiKnowledgeMessage::getTenantId, tenantId)
                .eq(AiKnowledgeMessage::getSessionId, sessionId)
                .eq(AiKnowledgeMessage::getDeleted, 0)
                .set(AiKnowledgeMessage::getDeleted, 1)
                .set(AiKnowledgeMessage::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public List<AiKnowledgeMessage> listBySession(Long sessionId) {
        return list(new LambdaQueryWrapperX<AiKnowledgeMessage>()
                .eq(AiKnowledgeMessage::getSessionId, sessionId)
                .eq(AiKnowledgeMessage::getDeleted, 0)
                .orderByAsc(AiKnowledgeMessage::getSeq)
                .orderByAsc(AiKnowledgeMessage::getId));
    }

    @Override
    public List<AiKnowledgeMessage> listByParent(Long parentId) {
        return list(new LambdaQueryWrapperX<AiKnowledgeMessage>()
                .eq(AiKnowledgeMessage::getParentId, parentId)
                .eq(AiKnowledgeMessage::getDeleted, 0)
                .orderByAsc(AiKnowledgeMessage::getVersion)
                .orderByAsc(AiKnowledgeMessage::getId));
    }

    @Override
    public List<AiKnowledgeMessage> listActiveBySession(Long sessionId) {
        return list(new LambdaQueryWrapperX<AiKnowledgeMessage>()
                .eq(AiKnowledgeMessage::getSessionId, sessionId)
                .eq(AiKnowledgeMessage::getIsActive, 1)
                .eq(AiKnowledgeMessage::getDeleted, 0)
                .orderByAsc(AiKnowledgeMessage::getSeq)
                .orderByAsc(AiKnowledgeMessage::getId));
    }

    @Override
    public Integer selectMaxSeq(Long sessionId) {
        Integer max = baseMapper.selectMaxSeq(requireTenantId(), sessionId);
        return max == null ? 0 : max;
    }

    @Override
    public AiKnowledgeMessage lockMessage(Long messageId) {
        return baseMapper.lockMessage(requireTenantId(), messageId);
    }

    @Override
    public List<AiKnowledgeMessage> findActivePath(Long sessionId, Long leafId) {
        if (leafId == null) {
            return List.of();
        }
        return baseMapper.findActivePath(requireTenantId(), sessionId, leafId);
    }

    @Override
    public Long findActiveLeaf(Long sessionId, Long targetId) {
        return baseMapper.findActiveLeaf(requireTenantId(), sessionId, targetId);
    }

    @Override
    public void switchSiblingActive(Long sessionId, Long parentId, String role, Integer seq, Long targetId) {
        Long tenantId = requireTenantId();
        baseMapper.deactivateSiblings(tenantId, sessionId, parentId, role, seq, targetId);
        baseMapper.activateSibling(tenantId, sessionId, targetId);
    }

    // ==================== 工具 ====================

    private static Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }

    private void fillCreateFields(AiKnowledgeMessage entity) {
        Long tenantId = requireTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (entity.getUserId() == null) {
            Assert.notNull(userId, "登录用户不能为空");
            entity.setUserId(userId);
        }
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
}
