package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.service.AiQueryMessageService;
import cn.iocoder.boot.module.data_query.service.AiQuerySessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * P7 对话服务：会话列表 / 消息历史 / 重新生成 / 修改问题 / 赞踩 / 删除会话。
 *
 * 安全红线：所有归属校验在本服务完成，userId 只从登录态取，绝不接受前端传入；
 * 会话 / 消息查询依赖 TenantDatabaseInterceptor 追加 tenant_id，跨租户记录与「不存在」同口径。
 * 重新生成 / 修改问题复用同一个 QueryOrchestrator，绝不绕过 S6/S7 闸门。
 */
@Slf4j
@Service
public class ChatConversationService {

    @Resource
    private AiQuerySessionService sessionService;

    @Resource
    private AiQueryMessageService messageService;

    @Resource
    private QueryOrchestrator queryOrchestrator;

    // ==================== 会话列表 / 删除 ====================

    /** 我的会话分页（服务端强制 userId = 登录用户） */
    public PageResult<AiQuerySession> listSessions(PageParam pageReqVO, String title, Long userId) {
        LambdaQueryWrapper<AiQuerySession> q = new LambdaQueryWrapper<AiQuerySession>()
                .eq(AiQuerySession::getUserId, userId)
                .eq(AiQuerySession::getDeleted, 0)
                .like(StringUtils.hasText(title), AiQuerySession::getTitle, title)
                .orderByDesc(AiQuerySession::getLastAskTime)
                .orderByDesc(AiQuerySession::getId);
        IPage<AiQuerySession> page = sessionService.page(MyBatisUtils.buildPage(pageReqVO), q);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /** 删除会话：会话软删 + 其下全部消息软删 */
    public void deleteSession(Long sessionId, Long userId) {
        AiQuerySession session = requireOwnedSession(sessionId, userId);
        sessionService.deleteAiQuerySession(session.getId());
        for (AiQueryMessage m : messageService.listBySession(session.getId())) {
            messageService.deleteAiQueryMessage(m.getId());
        }
    }

    /** 会话改名（只能改标题，前端不可改归属 / 状态 / 数据源） */
    public void renameSession(Long sessionId, String title, Long userId) {
        AiQuerySession session = requireOwnedSession(sessionId, userId);
        if (title == null || title.trim().isEmpty()) {
            return;
        }
        session.setTitle(title.trim().length() > 100 ? title.trim().substring(0, 100) : title.trim());
        sessionService.updateAiQuerySession(session);
    }

    // ==================== 消息历史 ====================

    /**
     * 会话当前分支的主链消息。
     *
     * <p>树模型下主链由 current_leaf_id 沿 parent_id 向上回溯唯一确定（设计文档 §6），
     * 这样切换版本后能看到旧分支上被选中的节点。"当前看不到"只因不在叶子路径上，不在于是否删除。
     *
     * <p>存量兼容：阶段 0 之前的历史数据是扁平模型（每轮 USER 的 parent_id 均为 NULL），
     * 严格回溯只能拿到当前一轮。因此当回溯结果不足 2 条（树断裂特征）时降级到旧的
     * is_active + seq 扫描，保证存量会话展示不退化；新数据 parent 连边完整，始终走树路径。
     */
    public List<AiQueryMessage> listMessages(Long sessionId, Long userId) {
        AiQuerySession session = requireOwnedSession(sessionId, userId);
        Long leafId = session.getCurrentLeafId();
        if (leafId != null && leafId > 0) {
            List<AiQueryMessage> path = messageService.findActivePath(sessionId, leafId);
            // 路径含根 + 当前问答至少 2 条即视为连边完整；否则视作存量断裂数据降级。
            if (path != null && path.size() >= 2) {
                return path;
            }
        }
        return messageService.listActiveBySession(sessionId);
    }

    /** 某条 USER 消息下的全部回答版本（供「查看历史版本 / 切换版本」） */
    public List<AiQueryMessage> listVersions(Long userMessageId, Long userId) {
        AiQueryMessage user = requireOwnedAssistantOrUser(userMessageId, userId);
        Long parent = AiQueryMessage.ROLE_USER.equals(user.getRole()) ? user.getId() : user.getParentId();
        return messageService.listByParent(parent);
    }

    /**
     * 某一轮次（seq）的全部「问题」版本：含被修改问题作废旧版本。
     *
     * 与 listVersions 的区别：listVersions 按 parentId 取「同一问题下的多个回答」（重新生成），
     * 这里取「同一个 seq 下被反复改写的多个 USER 问题 + 各自回答」（修改问题）。
     * recordEdit 用新 USER 替换旧 USER 且沿用 seq、version+1，旧 USER 及其回答 is_active=0，
     * 因此必须从 listBySession（含非主链）里按 seq 过滤，主链接口看不到这些历史版本。
     */
    public List<AiQueryMessage> listQuestionVersions(Long sessionId, Integer seq, Long userId) {
        requireOwnedSession(sessionId, userId);
        if (seq == null || seq <= 0) {
            return new ArrayList<>();
        }
        List<AiQueryMessage> all = messageService.listBySession(sessionId);
        // 修改问题产生的每个版本 USER 都占同一 seq、version 递增；按 version 升序即是切换顺序
        List<AiQueryMessage> users = all.stream()
                .filter(m -> AiQueryMessage.ROLE_USER.equals(m.getRole())
                        && m.getSeq() != null && seq.equals(m.getSeq()))
                .collect(Collectors.toList());
        if (users.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = users.stream().map(AiQueryMessage::getId).collect(Collectors.toSet());
        // 每个版本 USER 的回答：parentId 指向该 USER（回答版本可能有多条，一并带出）
        List<AiQueryMessage> assistants = all.stream()
                .filter(m -> AiQueryMessage.ROLE_ASSISTANT.equals(m.getRole())
                        && m.getParentId() != null && userIds.contains(m.getParentId()))
                .collect(Collectors.toList());
        List<AiQueryMessage> result = new ArrayList<>(users);
        result.addAll(assistants);
        return result;
    }

    // ==================== 切换版本 ====================

    /**
     * 切换当前分支到指定版本（设计文档 §5.4）。
     *
     * <p>不新增写接口：复用 versions 资源，GET 查版本、PUT 切版本。
     * 三步在同一事务且串行化：锁父节点 → 兄弟组内切换 is_active → 向下找激活叶子并推进
     * current_leaf_id。绝不能直接把叶子设为 target —— target 下可能还有 U→A 子节点，
     * 必须沿 is_active 向下找到真正的分支末端，切回后上下文才完整（§5.5）。
     *
     * <p>归属校验用 target 的 userId 与 sessionId 双重比对，跨租户/跨会话与「不存在」同口径。
     */
    @Transactional(rollbackFor = Exception.class)
    public void switchVersion(Long sessionId, Long targetMessageId, Long userId) {
        AiQuerySession session = requireOwnedSession(sessionId, userId);
        AiQueryMessage target = requireOwnedAssistantOrUser(targetMessageId, userId);
        if (!sessionId.equals(target.getSessionId())) {
            throw exception(AI_CHAT_MESSAGE_NOT_FOUND);
        }

        Long parentId = target.getParentId();
        // 锁父节点串行化同一兄弟组的并发切换；根 USER 兄弟组无父则锁 target 自身
        Long lockId = (parentId != null && parentId > 0) ? parentId : targetMessageId;
        messageService.lockMessage(lockId);

        // 兄弟组内切换：同 (parent_id, role, seq) 仅 target 置 1
        messageService.switchSiblingActive(sessionId, parentId, target.getRole(), target.getSeq(), targetMessageId);

        // 向下沿 is_active=1 找分支末端作为新叶子（找不到子节点时返回 target 自身）
        Long leafId = messageService.findActiveLeaf(sessionId, targetMessageId);
        sessionService.advanceLeaf(sessionId, leafId == null ? targetMessageId : leafId);
        log.info("[chat][switch-version] sessionId={} target={} parent={} role={} leaf={}",
                sessionId, targetMessageId, parentId, target.getRole(), leafId);
    }

    // ==================== 重新生成 / 修改问题 ====================

    /**
     * 重新生成：以源 ASSISTANT 消息对应问题的原话重跑整条链路。
     * 数据源 / 会话 / 问题文本全部来自已落库消息（服务端固化），前端只能传 messageId；
     * modelConfigId 是对话页「选择模型」的偏好，为空时沿用场景默认路由。
     */
    public AiChatAskRespVO regenerate(Long assistantMessageId, Long datasourceId, Long userId) {
        return regenerate(assistantMessageId, datasourceId, null, userId);
    }

    public AiChatAskRespVO regenerate(Long assistantMessageId, Long datasourceId,
                                      Long modelConfigId, Long userId) {
        AiQueryMessage old = requireOwnedAssistantOrUser(assistantMessageId, userId);
        if (!AiQueryMessage.ROLE_ASSISTANT.equals(old.getRole())) {
            throw exception(AI_CHAT_MESSAGE_ROLE_INVALID);
        }
        if (old.getIsActive() != null && old.getIsActive() == 0) {
            throw exception(AI_CHAT_REGENERATE_DISABLED);
        }
        AiQuerySession session = requireOwnedSession(old.getSessionId(), userId);
        String question = resolveQuestion(old, session);
        AiChatAskReqVO reqVO = new AiChatAskReqVO();
        reqVO.setQuestion(question);
        reqVO.setDatasourceId(datasourceId == null ? old.getDatasourceId() : datasourceId);
        reqVO.setSessionId(session.getId());
        reqVO.setSceneCode(session.getSceneCode());
        reqVO.setModelConfigId(modelConfigId);
        return queryOrchestrator.ask(reqVO, ChatMessageRecorder.OP_REGENERATE, old);
    }

    /**
     * 修改问题：改写问题文本后从该轮重跑（旧 USER + 旧 ASSISTANT 作废，生成新分支）。
     */
    public AiChatAskRespVO edit(Long userMessageId, String newQuestion, Long datasourceId, Long userId) {
        return edit(userMessageId, newQuestion, datasourceId, null, userId);
    }

    public AiChatAskRespVO edit(Long userMessageId, String newQuestion, Long datasourceId,
                                Long modelConfigId, Long userId) {
        AiQueryMessage old = requireOwnedAssistantOrUser(userMessageId, userId);
        if (!AiQueryMessage.ROLE_USER.equals(old.getRole())) {
            throw exception(AI_CHAT_MESSAGE_ROLE_INVALID);
        }
        if (!StringUtils.hasText(newQuestion)) {
            throw exception(AI_CHAT_QUESTION_EMPTY);
        }
        int maxLen = 500;
        if (newQuestion.length() > maxLen) {
            throw exception(AI_CHAT_QUESTION_TOO_LONG);
        }
        AiQuerySession session = requireOwnedSession(old.getSessionId(), userId);
        AiChatAskReqVO reqVO = new AiChatAskReqVO();
        reqVO.setQuestion(newQuestion.trim());
        reqVO.setDatasourceId(datasourceId == null ? old.getDatasourceId() : datasourceId);
        reqVO.setSessionId(session.getId());
        reqVO.setSceneCode(session.getSceneCode());
        reqVO.setModelConfigId(modelConfigId);
        return queryOrchestrator.ask(reqVO, ChatMessageRecorder.OP_EDIT, old);
    }

    // ==================== 赞踩 ====================

    /** 赞踩：即时幂等切换（-1 踩 / 0 取消 / 1 赞），仅 ASSISTANT 消息可评分 */
    public void rate(Long assistantMessageId, Integer rating, Long userId) {
        if (rating == null || rating < -1 || rating > 1) {
            throw exception(AI_CHAT_RATING_INVALID);
        }
        AiQueryMessage msg = requireOwnedAssistantOrUser(assistantMessageId, userId);
        if (!AiQueryMessage.ROLE_ASSISTANT.equals(msg.getRole())) {
            throw exception(AI_CHAT_MESSAGE_ROLE_INVALID);
        }
        msg.setRating(rating);
        messageService.updateAiQueryMessage(msg);
    }

    // ==================== 归属校验工具 ====================

    private AiQuerySession requireOwnedSession(Long sessionId, Long userId) {
        AiQuerySession session = sessionService.getAiQuerySession(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw exception(AI_CHAT_SESSION_NOT_OWNED);
        }
        return session;
    }

    private AiQueryMessage requireOwnedAssistantOrUser(Long messageId, Long userId) {
        AiQueryMessage msg = messageService.getAiQueryMessage(messageId);
        if (msg == null || !userId.equals(msg.getUserId())) {
            throw exception(AI_CHAT_MESSAGE_NOT_FOUND);
        }
        return msg;
    }

    /** 取原问题的文本：USER 消息直接取 question；ASSISTANT 消息取同 parent 的 USER */
    private String resolveQuestion(AiQueryMessage assistant, AiQuerySession session) {
        if (AiQueryMessage.ROLE_USER.equals(assistant.getRole()) && StringUtils.hasText(assistant.getQuestion())) {
            return assistant.getQuestion();
        }
        AiQueryMessage user = messageService.getAiQueryMessage(assistant.getParentId());
        if (user != null && StringUtils.hasText(user.getQuestion())) {
            return user.getQuestion();
        }
        if (StringUtils.hasText(session.getLastQuestion())) {
            return session.getLastQuestion();
        }
        throw exception(AI_CHAT_MESSAGE_NOT_FOUND);
    }
}
