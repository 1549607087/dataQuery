package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.boot.module.data_query.service.KnowledgeConversationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_QUESTION_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.AI_CHAT_QUESTION_TOO_LONG;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MESSAGE_ROLE_INVALID;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库问答「重跑类」门面。
 *
 * <p>为什么独立成 Bean：重新生成 / 修改问题需要同时依赖 {@link KnowledgeChatOrchestrator}
 * 与 {@link KnowledgeConversationService}，而编排器本身已依赖 ConversationService（历史拼装）。
 * 若把这两个方法塞进 ConversationService，就会与其形成循环依赖。这里用一个只进不出的 Facade
 * 收敛两者：ConversationService 负责归属校验与源消息读取，Orchestrator 负责重跑，本类只做装配。
 *
 * <p>与问数侧 {@code ChatConversationService.regenerate/edit} 逐行同构：
 * 数据源 / 会话 / 问题文本全部来自已落库消息（服务端固化），前端只能传 messageId + 新问题。
 */
@Service
public class KnowledgeChatFacade {

    @Resource
    private KnowledgeChatOrchestrator orchestrator;

    @Resource
    private KnowledgeConversationService conversationService;

    /**
     * 重新生成：以源 ASSISTANT 对应问题的原话重跑，旧答案退出选中、生成新版本。
     *
     * <p>历史版本（is_active=0）同样可作为重跑源：问题原文取自该版本自身记录，
     * 新答案由 TreeWriter 在同 parent 下生成兄弟版本并切为选中；旧版本不作废，仍可切回。
     * 这里仅保留角色校验，归属校验由 requireOwnedMessage 负责。
     */
    public AiKnowledgeChatRespVO regenerate(Long assistantMessageId, Long modelConfigId, Long userId) {
        return regenerate(assistantMessageId, modelConfigId, userId, null);
    }

    /** @param sink 流式输出口；同步调用传 null，行为与旧版一致 */
    public AiKnowledgeChatRespVO regenerate(Long assistantMessageId, Long modelConfigId, Long userId,
                                            ChatStageSink sink) {
        AiKnowledgeMessage old = conversationService.requireOwnedMessage(assistantMessageId);
        if (!AiKnowledgeMessage.ROLE_ASSISTANT.equals(old.getRole())) {
            throw exception(CHAT_MESSAGE_ROLE_INVALID);
        }
        AiKnowledgeSession session = conversationService.requireSession(old.getSessionId());
        String question = resolveQuestion(old, session);

        AiKnowledgeChatAskReqVO reqVO = new AiKnowledgeChatAskReqVO();
        reqVO.setBaseId(session.getBaseId());
        reqVO.setSessionId(session.getId());
        reqVO.setQuestion(question);
        reqVO.setModelConfigId(modelConfigId);
        return orchestrator.ask(reqVO, sink, KnowledgeMessageTreeWriter.OP_REGENERATE, old);
    }

    /**
     * 修改问题：改写问题文本后从该轮重跑，生成新分支（旧 USER 及其回答整支保留）。
     */
    public AiKnowledgeChatRespVO edit(Long userMessageId, String newQuestion, Long modelConfigId, Long userId) {
        return edit(userMessageId, newQuestion, modelConfigId, userId, null);
    }

    /** @param sink 流式输出口；同步调用传 null，行为与旧版一致 */
    public AiKnowledgeChatRespVO edit(Long userMessageId, String newQuestion, Long modelConfigId, Long userId,
                                      ChatStageSink sink) {
        AiKnowledgeMessage old = conversationService.requireOwnedMessage(userMessageId);
        if (!AiKnowledgeMessage.ROLE_USER.equals(old.getRole())) {
            throw exception(CHAT_MESSAGE_ROLE_INVALID);
        }
        if (!StringUtils.hasText(newQuestion)) {
            throw exception(AI_CHAT_QUESTION_EMPTY);
        }
        String question = newQuestion.trim();
        int maxLen = 500;
        if (question.length() > maxLen) {
            throw exception(AI_CHAT_QUESTION_TOO_LONG);
        }
        AiKnowledgeSession session = conversationService.requireSession(old.getSessionId());

        AiKnowledgeChatAskReqVO reqVO = new AiKnowledgeChatAskReqVO();
        reqVO.setBaseId(session.getBaseId());
        reqVO.setSessionId(session.getId());
        reqVO.setQuestion(question);
        reqVO.setModelConfigId(modelConfigId);
        return orchestrator.ask(reqVO, sink, KnowledgeMessageTreeWriter.OP_EDIT, old);
    }

    /** 源助手消息对应轮次的问题原文：优先 question 字段，缺失则沿父 USER 查 */
    private String resolveQuestion(AiKnowledgeMessage assistant, AiKnowledgeSession session) {
        if (StringUtils.hasText(assistant.getQuestion())) {
            return assistant.getQuestion();
        }
        return session.getLastQuestion();
    }
}
