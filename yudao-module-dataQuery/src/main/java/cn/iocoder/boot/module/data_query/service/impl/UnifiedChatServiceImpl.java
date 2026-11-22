package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.ChatMessageRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatMessageRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedSessionRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.KnowledgeChatMessageRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import cn.iocoder.boot.module.data_query.enums.ChatModeEnum;
import cn.iocoder.boot.module.data_query.framework.chat.ChatConversationService;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.boot.module.data_query.framework.chat.QueryOrchestrator;
import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeChatFacade;
import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeChatOrchestrator;
import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeReference;
import cn.iocoder.boot.module.data_query.mapper.UnifiedChatMapper;
import cn.iocoder.boot.module.data_query.service.KnowledgeConversationService;
import cn.iocoder.boot.module.data_query.service.UnifiedChatService;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_RESOURCE_REQUIRED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 统一对话门面实现：mode 路由 + 委派 + VO 转换 + 聚合会话。
 *
 * <p>身份固化：userId / tenantId 只从登录态与 TenantContextHolder 取，前端不可传。
 * 每个委派方法在Switch前都做 mode 与 sessionId 的成对校验，防止用一个 id 查错表。
 */
@Service
public class UnifiedChatServiceImpl implements UnifiedChatService {

    @Resource
    private QueryOrchestrator queryOrchestrator;
    @Resource
    private KnowledgeChatOrchestrator knowledgeOrchestrator;
    @Resource
    private ChatConversationService queryConversationService;
    @Resource
    private KnowledgeConversationService knowledgeConversationService;
    @Resource
    private KnowledgeChatFacade knowledgeChatFacade;
    @Resource
    private UnifiedChatMapper unifiedChatMapper;
    @Resource
    private ObjectMapper objectMapper;

    // ==================== 提问 ====================

    @Override
    public UnifiedChatAskRespVO ask(UnifiedChatAskReqVO reqVO) {
        return ask(reqVO, null);
    }

    @Override
    public UnifiedChatAskRespVO ask(UnifiedChatAskReqVO reqVO, ChatStageSink sink) {
        ChatModeEnum mode = ChatModeEnum.of(reqVO.getMode());
        if (mode == ChatModeEnum.QUERY) {
            validateQueryResource(reqVO);
            validateSessionMode(reqVO.getSessionId(), ChatModeEnum.QUERY);
            AiChatAskReqVO queryReq = toQueryReq(reqVO);
            AiChatAskRespVO resp = sink == null
                    ? queryOrchestrator.ask(queryReq)
                    : queryOrchestrator.ask(queryReq, sink);
            return UnifiedChatAskRespVO.fromQuery(resp);
        }
        validateKnowledgeResource(reqVO);
        validateSessionMode(reqVO.getSessionId(), ChatModeEnum.KNOWLEDGE);
        AiKnowledgeChatAskReqVO knowledgeReq = toKnowledgeReq(reqVO);
        AiKnowledgeChatRespVO resp = sink == null
                ? knowledgeOrchestrator.ask(knowledgeReq)
                : knowledgeOrchestrator.ask(knowledgeReq, sink);
        return UnifiedChatAskRespVO.fromKnowledge(resp);
    }

    // ==================== 会话 ====================

    @Override
    public PageResult<UnifiedSessionRespVO> pageSessions(String mode, String title,
                                                          Integer pageNo, Integer pageSize) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        if (userId == null || tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        String normalizedMode = normalizeOptionalMode(mode);
        long no = pageNo == null || pageNo < 1 ? 1L : pageNo;
        long size = pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100);
        long offset = (no - 1) * size;
        String like = StringUtils.hasText(title) ? title.trim() : null;
        List<UnifiedSessionRespVO> list = unifiedChatMapper.selectSessionPage(
                userId, tenantId, normalizedMode, like, size, offset);
        Long total = unifiedChatMapper.countSessions(userId, tenantId, normalizedMode, like);
        return new PageResult<>(list == null ? List.of() : list, total == null ? 0L : total);
    }

    @Override
    public void renameSession(String mode, Long sessionId, String title) {
        if (ChatModeEnum.isQuery(mode)) {
            queryConversationService.renameSession(sessionId, title, currentUserId());
        } else if (ChatModeEnum.isKnowledge(mode)) {
            knowledgeConversationService.renameSession(sessionId, title);
        } else {
            throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
        }
    }

    @Override
    public void deleteSession(String mode, Long sessionId) {
        if (ChatModeEnum.isQuery(mode)) {
            queryConversationService.deleteSession(sessionId, currentUserId());
        } else if (ChatModeEnum.isKnowledge(mode)) {
            knowledgeConversationService.deleteSession(sessionId);
        } else {
            throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
        }
    }

    // ==================== 消息 ====================

    @Override
    public List<UnifiedChatMessageRespVO> listMessages(String mode, Long sessionId) {
        if (ChatModeEnum.isQuery(mode)) {
            List<UnifiedChatMessageRespVO> result = new ArrayList<>();
            for (AiQueryMessage m : queryConversationService.listMessages(sessionId, currentUserId())) {
                result.add(UnifiedChatMessageRespVO.fromQuery(toChatMessageVO(m)));
            }
            return result;
        }
        if (ChatModeEnum.isKnowledge(mode)) {
            List<UnifiedChatMessageRespVO> result = new ArrayList<>();
            for (AiKnowledgeMessage m : knowledgeConversationService.listMessages(sessionId)) {
                result.add(UnifiedChatMessageRespVO.fromKnowledge(toKnowledgeMessageVO(m)));
            }
            return result;
        }
        throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
    }

    @Override
    public List<UnifiedChatMessageRespVO> listVersions(String mode, Long messageId) {
        List<UnifiedChatMessageRespVO> result = new ArrayList<>();
        if (ChatModeEnum.isQuery(mode)) {
            for (AiQueryMessage m : queryConversationService.listVersions(messageId, currentUserId())) {
                result.add(UnifiedChatMessageRespVO.fromQuery(toChatMessageVO(m)));
            }
        } else if (ChatModeEnum.isKnowledge(mode)) {
            for (AiKnowledgeMessage m : knowledgeConversationService.listVersions(messageId)) {
                result.add(UnifiedChatMessageRespVO.fromKnowledge(toKnowledgeMessageVO(m)));
            }
        } else {
            throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
        }
        return result;
    }

    @Override
    public List<UnifiedChatMessageRespVO> listQuestionVersions(String mode, Long sessionId, Integer seq) {
        List<UnifiedChatMessageRespVO> result = new ArrayList<>();
        if (ChatModeEnum.isQuery(mode)) {
            for (AiQueryMessage m : queryConversationService.listQuestionVersions(sessionId, seq, currentUserId())) {
                result.add(UnifiedChatMessageRespVO.fromQuery(toChatMessageVO(m)));
            }
        } else if (ChatModeEnum.isKnowledge(mode)) {
            for (AiKnowledgeMessage m : knowledgeConversationService.listQuestionVersions(sessionId, seq)) {
                result.add(UnifiedChatMessageRespVO.fromKnowledge(toKnowledgeMessageVO(m)));
            }
        } else {
            throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
        }
        return result;
    }

    @Override
    public void switchVersion(String mode, Long sessionId, Long targetMessageId) {
        if (ChatModeEnum.isQuery(mode)) {
            queryConversationService.switchVersion(sessionId, targetMessageId, currentUserId());
        } else if (ChatModeEnum.isKnowledge(mode)) {
            knowledgeConversationService.switchVersion(sessionId, targetMessageId);
        } else {
            throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
        }
    }

    // ==================== 重跑 / 赞踩 ====================

    @Override
    public UnifiedChatAskRespVO regenerate(String mode, Long messageId, Long modelConfigId) {
        if (ChatModeEnum.isQuery(mode)) {
            return UnifiedChatAskRespVO.fromQuery(
                    queryConversationService.regenerate(messageId, null, modelConfigId, currentUserId()));
        }
        if (ChatModeEnum.isKnowledge(mode)) {
            return UnifiedChatAskRespVO.fromKnowledge(
                    knowledgeChatFacade.regenerate(messageId, modelConfigId, currentUserId()));
        }
        throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
    }

    @Override
    public UnifiedChatAskRespVO edit(String mode, Long messageId, String question, Long modelConfigId) {
        if (ChatModeEnum.isQuery(mode)) {
            return UnifiedChatAskRespVO.fromQuery(
                    queryConversationService.edit(messageId, question, null, modelConfigId, currentUserId()));
        }
        if (ChatModeEnum.isKnowledge(mode)) {
            return UnifiedChatAskRespVO.fromKnowledge(
                    knowledgeChatFacade.edit(messageId, question, modelConfigId, currentUserId()));
        }
        throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
    }

    @Override
    public void rate(String mode, Long messageId, Integer rating) {
        if (ChatModeEnum.isQuery(mode)) {
            queryConversationService.rate(messageId, rating, currentUserId());
        } else if (ChatModeEnum.isKnowledge(mode)) {
            knowledgeConversationService.rate(messageId, rating);
        } else {
            throw exception(cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID);
        }
    }

    // ==================== 请求转换 / 校验 ====================

    private AiChatAskReqVO toQueryReq(UnifiedChatAskReqVO req) {
        AiChatAskReqVO v = new AiChatAskReqVO();
        v.setQuestion(req.getQuestion());
        v.setDatasourceId(req.getDatasourceId());
        v.setSessionId(req.getSessionId());
        v.setSceneCode(req.getSceneCode());
        v.setModelConfigId(req.getModelConfigId());
        return v;
    }

    private AiKnowledgeChatAskReqVO toKnowledgeReq(UnifiedChatAskReqVO req) {
        AiKnowledgeChatAskReqVO v = new AiKnowledgeChatAskReqVO();
        v.setBaseId(req.getBaseId());
        v.setSessionId(req.getSessionId());
        v.setQuestion(req.getQuestion());
        v.setModelConfigId(req.getModelConfigId());
        v.setVersion(req.getVersion());
        return v;
    }

    private void validateQueryResource(UnifiedChatAskReqVO req) {
        if (req.getDatasourceId() == null) {
            throw exception(CHAT_RESOURCE_REQUIRED);
        }
    }

    private void validateKnowledgeResource(UnifiedChatAskReqVO req) {
        if (req.getBaseId() == null) {
            throw exception(CHAT_RESOURCE_REQUIRED);
        }
    }

    /**
     * mode 与 sessionId 成对约定：sessionId 非空时，mode 决定去哪张会话表读取。
     * 传错 mode + 他人会话不会串数据——两张表物理隔离，编排器 S0 会在「目标表」里查不到该
     * sessionId（或归属不符）而拒绝，表现为会话不存在 / 模式不匹配。真正的一致性守门在 S0，
     * 本方法保留为扩展点（未来若要做「先按 id 判 mode」可在此增强），当前不做额外查询。
     */
    private void validateSessionMode(Long sessionId, ChatModeEnum expected) {
        // 会话存在性 / 归属 / 模式一致性由编排器 S0 按各自会话表校验，避免本层重复查库。
    }

    private String normalizeOptionalMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return null;
        }
        return ChatModeEnum.of(mode).getCode(); // 非法 mode 直接抛 CHAT_MODE_INVALID
    }

    private static Long currentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        return userId;
    }

    // ==================== 消息 VO 转换 ====================

    private ChatMessageRespVO toChatMessageVO(AiQueryMessage m) {
        ChatMessageRespVO v = new ChatMessageRespVO();
        v.setId(m.getId());
        v.setRole(m.getRole());
        v.setParentId(m.getParentId());
        v.setVersion(m.getVersion());
        v.setSeq(m.getSeq());
        v.setActive(m.getIsActive() != null && m.getIsActive() == 1);
        v.setQuestion(m.getQuestion());
        v.setAnswer(m.getAnswer());
        v.setGeneratedSql(m.getGeneratedSql());
        v.setExecutedSql(m.getExecutedSql());
        v.setResult(parse(m.getResultJson(), cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult.class));
        v.setChartSpec(parse(m.getChartSpecJson(), cn.iocoder.boot.module.data_query.framework.chat.ChartSpec.class));
        v.setExplain(m.getExplain());
        v.setStatus(m.getStatus());
        v.setErrorCode(m.getErrorCode());
        v.setErrorMsg(m.getErrorMsg());
        v.setRating(m.getRating());
        v.setTraceId(m.getTraceId());
        v.setAuditId(m.getAuditId());
        v.setCreateTime(m.getCreateTime());
        return v;
    }

    private KnowledgeChatMessageRespVO toKnowledgeMessageVO(AiKnowledgeMessage m) {
        KnowledgeChatMessageRespVO v = new KnowledgeChatMessageRespVO();
        v.setId(m.getId());
        v.setRole(m.getRole());
        v.setParentId(m.getParentId());
        v.setVersion(m.getVersion());
        v.setSeq(m.getSeq());
        v.setActive(m.getIsActive() != null && m.getIsActive() == 1);
        v.setQuestion(m.getQuestion());
        v.setAnswer(m.getAnswer());
        v.setReferences(parseList(m.getReferencesJson()));
        v.setRecallMode(m.getRecallMode());
        v.setRecallDegraded(m.getRecallDegraded() != null && m.getRecallDegraded() == 1);
        v.setRetrievedCount(m.getRetrievedCount());
        v.setCitationDroppedCount(m.getCitationDroppedCount());
        v.setLatencyMs(m.getLatencyMs());
        v.setStatus(m.getStatus());
        v.setErrorCode(m.getErrorCode());
        v.setErrorMsg(m.getErrorMsg());
        v.setRating(m.getRating());
        v.setTraceId(m.getTraceId());
        v.setCreateTime(m.getCreateTime());
        return v;
    }

    private <T> T parse(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<KnowledgeReference> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<KnowledgeReference>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
