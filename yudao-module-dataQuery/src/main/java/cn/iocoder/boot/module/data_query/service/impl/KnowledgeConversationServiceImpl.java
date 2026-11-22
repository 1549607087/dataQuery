package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.boot.module.data_query.service.KnowledgeConversationService;
import cn.iocoder.boot.module.data_query.service.KnowledgeMessageService;
import cn.iocoder.boot.module.data_query.service.KnowledgeSessionService;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MESSAGE_ROLE_INVALID;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_RATING_INVALID;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_SESSION_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库问答会话门面实现
 *
 * <p>租户隔离由 TenantDatabaseInterceptor 追加 tenant_id，本层再叠加「必须本人」的用户过滤：
 * 同一租户内不同用户的会话也互不可见（会话列表是个人工作区，不是租户共享资源）。
 */
@Slf4j
@Service
public class KnowledgeConversationServiceImpl implements KnowledgeConversationService {

    /** 单条历史消息进入提示词的字符上限，避免一条超长回答挤掉全部历史 */
    private static final int HISTORY_LINE_MAX = 800;
    /** 会话标题上限 */
    private static final int TITLE_MAX_LEN = 100;

    @Resource
    private KnowledgeSessionService sessionService;

    @Resource
    private KnowledgeMessageService messageService;

    @Override
    public AiKnowledgeSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        AiKnowledgeSession session = sessionService.getKnowledgeSession(sessionId);
        // 越权与不存在返回同一错误码：不泄露他人会话是否存在
        if (session == null) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId != null && !userId.equals(session.getUserId())) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        return session;
    }

    @Override
    public PageResult<AiKnowledgeSession> pageSession(Long baseId, Integer pageNo, Integer pageSize) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        long no = pageNo == null || pageNo < 1 ? 1L : pageNo;
        // 上限 100：会话列表是侧边栏，不需要更大页
        long size = pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100);
        Page<AiKnowledgeSession> page = sessionService.page(new Page<>(no, size),
                new LambdaQueryWrapperX<AiKnowledgeSession>()
                        .eq(AiKnowledgeSession::getUserId, userId)
                        .eqIfPresent(AiKnowledgeSession::getBaseId, baseId)
                        .eq(AiKnowledgeSession::getDeleted, 0)
                        .orderByDesc(AiKnowledgeSession::getLastAskTime)
                        .orderByDesc(AiKnowledgeSession::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public void renameSession(Long sessionId, String title) {
        if (!StringUtils.hasText(title)) {
            return; // 空标题不改，避免把首问生成的标题清掉
        }
        requireSession(sessionId);
        AiKnowledgeSession update = new AiKnowledgeSession();
        update.setId(sessionId);
        update.setTitle(clip(title.trim(), TITLE_MAX_LEN));
        sessionService.updateKnowledgeSession(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        requireSession(sessionId);
        // 先软删消息再软删会话：单条 UPDATE，避免逐条循环的 N+1
        messageService.deleteBySession(sessionId);
        sessionService.deleteKnowledgeSession(sessionId);
    }

    @Override
    public List<AiKnowledgeMessage> listMessages(Long sessionId) {
        AiKnowledgeSession session = requireSession(sessionId);
        List<AiKnowledgeMessage> path = messageService.findActivePath(sessionId, session.getCurrentLeafId());
        if (path != null && !path.isEmpty()) {
            return path;
        }
        // 降级：current_leaf_id 为空（存量数据 / 指针未推进）时退化为「全部选中消息」
        List<AiKnowledgeMessage> active = messageService.listActiveBySession(sessionId);
        return active == null ? List.of() : active;
    }

    @Override
    public AiKnowledgeMessage requireOwnedMessage(Long messageId) {
        if (messageId == null) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        AiKnowledgeMessage msg = messageService.getKnowledgeMessage(messageId);
        if (msg == null) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        // 双重校验：消息本人 + 其所属会话本人（消息 userId 理论上与会话一致，防御存量脏数据）
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId != null && !userId.equals(msg.getUserId())) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        AiKnowledgeSession session = sessionService.getKnowledgeSession(msg.getSessionId());
        if (session == null || (userId != null && !userId.equals(session.getUserId()))) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        return msg;
    }

    @Override
    public List<AiKnowledgeMessage> listVersions(Long messageId) {
        AiKnowledgeMessage msg = requireOwnedMessage(messageId);
        // 助手消息：parent 是 USER，列同 USER 下的全部 ASSISTANT 兄弟
        // 用户消息：parent 是上一条 ASSISTANT，列同 parent 下的全部 USER 兄弟
        Long parent = msg.getParentId();
        String role = AiKnowledgeMessage.ROLE_ASSISTANT.equals(msg.getRole())
                ? AiKnowledgeMessage.ROLE_ASSISTANT : AiKnowledgeMessage.ROLE_USER;
        List<AiKnowledgeMessage> versions = messageService.listByParent(parent);
        List<AiKnowledgeMessage> result = new ArrayList<>();
        if (versions == null) {
            return result;
        }
        for (AiKnowledgeMessage v : versions) {
            if (role.equals(v.getRole())) {
                result.add(v);
            }
        }
        return result;
    }

    @Override
    public List<AiKnowledgeMessage> listQuestionVersions(Long sessionId, Integer seq) {
        requireSession(sessionId);
        if (seq == null || seq <= 0) {
            return List.of();
        }
        List<AiKnowledgeMessage> all = messageService.listBySession(sessionId);
        List<AiKnowledgeMessage> result = new ArrayList<>();
        if (all == null) {
            return result;
        }
        for (AiKnowledgeMessage m : all) {
            if (seq.equals(m.getSeq()) || Integer.valueOf(seq + 1).equals(m.getSeq())) {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public void switchVersion(Long sessionId, Long targetMessageId) {
        requireSession(sessionId);
        AiKnowledgeMessage target = requireOwnedMessage(targetMessageId);
        if (!sessionId.equals(target.getSessionId())) {
            throw exception(KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND);
        }
        Long parentId = target.getParentId();
        Long lockId = (parentId != null && parentId > 0) ? parentId : targetMessageId;
        messageService.lockMessage(lockId);
        messageService.switchSiblingActive(sessionId, parentId, target.getRole(), target.getSeq(), targetMessageId);
        Long leafId = messageService.findActiveLeaf(sessionId, targetMessageId);
        sessionService.advanceLeaf(sessionId, leafId == null ? targetMessageId : leafId, null, false);
        log.info("[knowledge-chat][switch-version] sessionId={} target={} parent={} role={} leaf={}",
                sessionId, targetMessageId, parentId, target.getRole(), leafId);
    }

    @Override
    public void rate(Long assistantMessageId, Integer rating) {
        if (rating == null || rating < -1 || rating > 1) {
            throw exception(CHAT_RATING_INVALID);
        }
        AiKnowledgeMessage msg = requireOwnedMessage(assistantMessageId);
        if (!AiKnowledgeMessage.ROLE_ASSISTANT.equals(msg.getRole())) {
            throw exception(CHAT_MESSAGE_ROLE_INVALID);
        }
        AiKnowledgeMessage update = new AiKnowledgeMessage();
        update.setId(assistantMessageId);
        update.setRating(rating);
        messageService.updateKnowledgeMessage(update);
    }

    @Override
    public HistoryResult buildHistory(Long sessionId, int maxMessages, int tokenBudget) {
        HistoryResult result = new HistoryResult();
        if (sessionId == null) {
            return result;
        }
        AiKnowledgeSession session = sessionService.getKnowledgeSession(sessionId);
        if (session == null || session.getCurrentLeafId() == null) {
            return result;
        }
        List<AiKnowledgeMessage> path = messageService.findActivePath(sessionId, session.getCurrentLeafId());
        if (path == null || path.isEmpty()) {
            return result;
        }

        // 只保留有文本的 USER / ASSISTANT（失败轮次的空回答不进历史，避免污染上下文）
        List<AiKnowledgeMessage> usable = new ArrayList<>(path.size());
        for (AiKnowledgeMessage m : path) {
            if (AiKnowledgeMessage.ROLE_USER.equals(m.getRole()) && StringUtils.hasText(m.getQuestion())) {
                usable.add(m);
            } else if (AiKnowledgeMessage.ROLE_ASSISTANT.equals(m.getRole()) && StringUtils.hasText(m.getAnswer())) {
                usable.add(m);
            }
        }
        if (usable.isEmpty()) {
            return result;
        }

        // 只取最近 maxMessages 条
        int max = maxMessages <= 0 ? 6 : maxMessages;
        int from = Math.max(0, usable.size() - max);
        List<AiKnowledgeMessage> tail = usable.subList(from, usable.size());

        // 从新到旧累加，超预算即停：保证「最近的轮次」优先保留
        LinkedList<String> lines = new LinkedList<>();
        int used = 0;
        int turns = 0;
        for (int i = tail.size() - 1; i >= 0; i--) {
            AiKnowledgeMessage m = tail.get(i);
            String line = buildLine(m);
            if (line == null) {
                continue;
            }
            if (!lines.isEmpty() && used + line.length() > tokenBudget) {
                break;
            }
            lines.addFirst(line);
            used += line.length() + 1;
            if (AiKnowledgeMessage.ROLE_ASSISTANT.equals(m.getRole())) {
                turns++;
            }
        }
        if (lines.isEmpty()) {
            return result;
        }
        result.setText(String.join("\n", lines));
        result.setMessageCount(lines.size());
        result.setTurnCount(turns);
        return result;
    }

    // ==================== 工具 ====================

    private static String buildLine(AiKnowledgeMessage m) {
        if (AiKnowledgeMessage.ROLE_USER.equals(m.getRole())) {
            String q = m.getQuestion().trim();
            return "用户：" + clip(q, HISTORY_LINE_MAX);
        }
        if (AiKnowledgeMessage.ROLE_ASSISTANT.equals(m.getRole())) {
            String a = m.getAnswer().trim();
            return "助手：" + clip(a, HISTORY_LINE_MAX);
        }
        return null;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
