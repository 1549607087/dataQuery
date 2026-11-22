package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.boot.module.data_query.mapper.KnowledgeSessionMapper;
import cn.iocoder.boot.module.data_query.service.KnowledgeSessionService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_SESSION_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库问答会话 Service 实现
 *
 * <p>实体是普通 POJO，框架不会自动填充审计字段；运行时表全租户私有，必须显式写 tenant_id。
 */
@Service
public class KnowledgeSessionServiceImpl extends ServiceImpl<KnowledgeSessionMapper, AiKnowledgeSession>
        implements KnowledgeSessionService {

    private static final String SCENE_RAG = "RAG";
    private static final int TITLE_MAX_LEN = 30;

    @Override
    public AiKnowledgeSession getKnowledgeSession(Long id) {
        if (id == null) {
            return null;
        }
        List<AiKnowledgeSession> list = list(new LambdaQueryWrapperX<AiKnowledgeSession>()
                .eq(AiKnowledgeSession::getId, id)
                .eq(AiKnowledgeSession::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiKnowledgeSession createSession(Long baseId, String question, String traceId) {
        AiKnowledgeSession session = new AiKnowledgeSession();
        session.setBaseId(baseId);
        session.setTitle(abbreviate(question, TITLE_MAX_LEN));
        session.setSessionNo(nextSessionNo());
        session.setSceneCode(SCENE_RAG);
        session.setStatus(AiKnowledgeSession.STATUS_ACTIVE);
        session.setMessageCount(0);
        session.setLastQuestion(question);
        session.setLastAskTime(LocalDateTime.now());
        session.setTraceId(traceId == null ? "" : traceId);
        session.setRemark(null);
        createKnowledgeSession(session);
        return session;
    }

    @Override
    public Long createKnowledgeSession(AiKnowledgeSession entity) {
        Assert.notNull(entity, "entity 不能为空");
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateKnowledgeSession(AiKnowledgeSession entity) {
        Assert.notNull(entity, "entity 不能为空");
        if (getKnowledgeSession(entity.getId()) == null) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteKnowledgeSession(Long id) {
        if (getKnowledgeSession(id) == null) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        update(new LambdaUpdateWrapper<AiKnowledgeSession>()
                .eq(AiKnowledgeSession::getId, id)
                .set(AiKnowledgeSession::getDeleted, 1)
                .set(AiKnowledgeSession::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public AiKnowledgeSession lockSession(Long sessionId) {
        Long tenantId = requireTenantId();
        return baseMapper.lockSession(tenantId, sessionId);
    }

    @Override
    public void advanceLeaf(Long sessionId, Long leafId, String lastQuestion, boolean countTurn) {
        if (getKnowledgeSession(sessionId) == null) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        LambdaUpdateWrapper<AiKnowledgeSession> wrapper = new LambdaUpdateWrapper<AiKnowledgeSession>()
                .eq(AiKnowledgeSession::getId, sessionId)
                .eq(AiKnowledgeSession::getDeleted, 0)
                .set(AiKnowledgeSession::getCurrentLeafId, leafId)
                .set(AiKnowledgeSession::getLastAskTime, LocalDateTime.now())
                .set(AiKnowledgeSession::getUpdateTime, LocalDateTime.now());
        if (StringUtils.hasText(lastQuestion)) {
            wrapper.set(AiKnowledgeSession::getLastQuestion, lastQuestion);
        }
        if (countTurn) {
            // 原子自增，避免并发 ASK 读改写丢失；常量 2 由代码控制，不接受外部输入
            wrapper.setSql("message_count = message_count + 2");
        }
        update(wrapper);
    }

    // ==================== 工具 ====================

    /** 会话编号：KS + 毫秒时间戳 + 6 位大写随机，租户内唯一索引兜底 */
    private static String nextSessionNo() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(20);
        sb.append("KS").append(System.currentTimeMillis());
        for (int i = 0; i < 6; i++) {
            sb.append((char) ('A' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static String abbreviate(String question, int maxLen) {
        if (!StringUtils.hasText(question)) {
            return "知识库问答";
        }
        String trimmed = question.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private static Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }

    private void fillCreateFields(AiKnowledgeSession entity) {
        Long tenantId = requireTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Assert.notNull(userId, "登录用户不能为空");
        String user = userId.toString();
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 ASSIGN_ID 生成
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setCreator(user);
        entity.setUpdater(user);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void fillUpdateFields(AiKnowledgeSession entity) {
        Long tenantId = requireTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        entity.setTenantId(tenantId);
        entity.setUpdater(userId == null ? "" : userId.toString());
        entity.setUpdateTime(LocalDateTime.now());
    }
}
