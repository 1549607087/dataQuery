package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.boot.module.data_query.service.KnowledgeMessageService;
import cn.iocoder.boot.module.data_query.service.KnowledgeSessionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_SESSION_BASE_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHAT_SESSION_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 知识库对话树写入器：在一次事务内完成「建兄弟 + 推进叶子」的落库编排。
 *
 * <p>为什么独立成 Bean：调用方编排器需要吞异常降级（落库失败不能影响问答终态）。
 * 若把 {@code @Transactional} 放在编排器同一类的方法上，自调用不走代理、事务失效，
 * 且异常被吞掉后也不会回滚。这里把事务方法放到独立 Bean，由编排器通过 Spring 代理调用：
 * 方法内抛异常 → 整笔回滚 → 异常外抛 → 编排器 try-catch 降级，二者互不干扰。
 *
 * <p>串行化：入口先 {@code lockSession} 对会话行加 {@code FOR UPDATE}，把
 * 「算 seq → 读叶子 → 落两条消息 → 推进叶子」整段串成临界区。同一会话的并发提问
 * 因此不会拿到相同的 seq，也不会互相覆盖 current_leaf_id（唯一索引
 * {@code uk_ai_knowledge_message_sibling_version} 作为最后一道兜底）。
 *
 * <p>树语义与问数消息一致：
 * <ul>
 *   <li>ASK：新 USER 的 parent = 会话当前 current_leaf_id（首条为 NULL 根），叶子推进到新 ASSISTANT；</li>
 *   <li>REGENERATE：新 ASSISTANT 与源 ASSISTANT 同 parent（同一 USER），version 取兄弟最大值 +1，
 *       兄弟切换使旧答案退出选中，叶子推进到新 ASSISTANT；</li>
 *   <li>EDIT：新 USER 与源 USER 同 parent（上一条 ASSISTANT），version 取兄弟最大值 +1，
 *       兄弟切换使旧问题退出选中；旧 USER 及其回答整支保留，不作废——切回旧版本时仍可沿树找回。</li>
 * </ul>
 *
 * <p>轮次计数：只有 ASK 算新轮次（{@code countTurn=true}，message_count 原子 +2）；
 * REGENERATE / EDIT 是同轮内的版本迭代，{@code countTurn=false}，不能虚增轮次。
 */
@Slf4j
@Component
public class KnowledgeMessageTreeWriter {

    public static final String OP_ASK = "ASK";
    public static final String OP_REGENERATE = "REGENERATE";
    public static final String OP_EDIT = "EDIT";

    @Resource
    private KnowledgeMessageService messageService;

    @Resource
    private KnowledgeSessionService sessionService;

    @Resource
    private KnowledgeProperties properties;

    /**
     * 事务入口：先判定本轮该不该切版本，再按操作类型分派。
     *
     * <p>重跑失败 / 空中断时直接返回 null：不建节点、不切选中、不推进叶子。
     * 旧答案保持在墙上，用户刷新后看到的仍是原来那份正确回答。
     *
     * @return 新落库的 ASSISTANT 消息 id；本轮按策略不落库时为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long write(KnowledgeChatContext ctx) {
        int minKeepChars = properties.getStopKeepMinChars() == null ? 10 : properties.getStopKeepMinChars();
        if (!PromotionPolicy.shouldPersist(ctx, minKeepChars)) {
            log.info("[knowledge-chat][message-record] 跳过落库 op={} state={} pushedChars={} traceId={}",
                    ctx.getMessageOp(), ctx.getTerminalState(), ctx.getPushedChars(), ctx.getTraceId());
            return null;
        }
        String op = StringUtils.hasText(ctx.getMessageOp()) ? ctx.getMessageOp() : OP_ASK;
        return switch (op) {
            case OP_REGENERATE -> recordRegenerate(ctx);
            case OP_EDIT -> recordEdit(ctx);
            default -> recordAsk(ctx);
        };
    }

    // ==================== ASK ====================

    /** 普通提问：parent = 当前叶子，新建 USER + ASSISTANT，叶子推进到 ASSISTANT（计数） */
    private Long recordAsk(KnowledgeChatContext ctx) {
        AiKnowledgeSession session = lockAndVerify(ctx);
        int seq = nextSeq(ctx.getSessionId());
        Long parent = session.getCurrentLeafId(); // 首条为 null（根）

        // ---------- USER ----------
        AiKnowledgeMessage user = buildBase(ctx);
        user.setRole(AiKnowledgeMessage.ROLE_USER);
        user.setParentId(parent);
        user.setRootMessageId(0L); // 落库后回填自己 id
        user.setVersion(1);
        user.setSeq(seq);
        user.setQuestion(ctx.getQuestion());
        user.setStatus(statusOf(ctx));
        Long userMessageId = messageService.createKnowledgeMessage(user);
        user.setRootMessageId(userMessageId);
        messageService.updateKnowledgeMessage(user);

        // ---------- ASSISTANT ----------
        AiKnowledgeMessage assistant = buildAssistant(ctx, userMessageId, 1, seq + 1);
        Long assistantId = messageService.createKnowledgeMessage(assistant);

        // 一轮 = USER + ASSISTANT，message_count 原子 +2
        sessionService.advanceLeaf(ctx.getSessionId(), assistantId, ctx.getQuestion(), true);

        ctx.setUserMessageId(userMessageId);
        ctx.setAssistantMessageId(assistantId);
        ctx.setAssistantVersion(1);
        return assistantId;
    }

    // ==================== REGENERATE ====================

    /** 重新生成：新 ASSISTANT 与源 ANSWER 同 parent（同一 USER），只切换 ASSISTANT 兄弟（不计数） */
    private Long recordRegenerate(KnowledgeChatContext ctx) {
        AiKnowledgeMessage old = ctx.getSourceMessage();
        if (old == null || !AiKnowledgeMessage.ROLE_ASSISTANT.equals(old.getRole())) {
            return recordAsk(ctx); // 源缺失 / 角色不符，防御性降级
        }
        lockAndVerify(ctx); // 对会话行加锁，串行化本会话内的写操作
        Long parentUser = old.getParentId();
        if (parentUser == null || parentUser == 0L) {
            return recordAsk(ctx); // 存量异常数据：无父 USER，无法建兄弟
        }

        // 锁父 USER，串行化同一问题下的并发重新生成（version MAX+1 竞态兜底）
        messageService.lockMessage(parentUser);

        int seq = old.getSeq() == null ? nextSeq(ctx.getSessionId()) : old.getSeq();
        int version = nextVersion(parentUser, AiKnowledgeMessage.ROLE_ASSISTANT);
        Long rootId = old.getRootMessageId() == null ? parentUser : old.getRootMessageId();
        AiKnowledgeMessage assistant = buildAssistant(ctx, parentUser, version, seq, rootId);
        Long assistantId = messageService.createKnowledgeMessage(assistant);

        // 兄弟切换：同 (parent=USER, role=ASSISTANT, seq) 旧答案置 0，新答案置 1
        messageService.switchSiblingActive(ctx.getSessionId(), parentUser,
                AiKnowledgeMessage.ROLE_ASSISTANT, seq, assistantId);

        // 同轮版本迭代，不新增轮次
        sessionService.advanceLeaf(ctx.getSessionId(), assistantId, ctx.getQuestion(), false);

        ctx.setUserMessageId(parentUser);
        ctx.setAssistantMessageId(assistantId);
        ctx.setAssistantVersion(version);
        return assistantId;
    }

    // ==================== EDIT ====================

    /** 修改问题：新 USER 与源 USER 同 parent（上一条 ASSISTANT），切换 USER 兄弟并推进叶子（不计数） */
    private Long recordEdit(KnowledgeChatContext ctx) {
        AiKnowledgeMessage oldUser = ctx.getSourceMessage();
        if (oldUser == null || !AiKnowledgeMessage.ROLE_USER.equals(oldUser.getRole())) {
            return recordAsk(ctx);
        }
        lockAndVerify(ctx);
        Long parentAssistant = oldUser.getParentId(); // 上一条 ASSISTANT；存量扁平数据可能为 null

        if (parentAssistant != null && parentAssistant != 0L) {
            messageService.lockMessage(parentAssistant);
        }

        int seq = oldUser.getSeq() == null ? nextSeq(ctx.getSessionId()) : oldUser.getSeq();
        int userVersion = nextVersion(parentAssistant, AiKnowledgeMessage.ROLE_USER);

        AiKnowledgeMessage user = buildBase(ctx);
        user.setRole(AiKnowledgeMessage.ROLE_USER);
        user.setParentId(parentAssistant); // 关键：与旧 USER 同 parent，旧分支整支保留
        user.setRootMessageId(0L);
        user.setQuestion(ctx.getQuestion());
        user.setVersion(userVersion);
        user.setSeq(seq);
        user.setStatus(statusOf(ctx));
        Long userMessageId = messageService.createKnowledgeMessage(user);
        user.setRootMessageId(userMessageId);
        messageService.updateKnowledgeMessage(user);

        // 只切 USER 兄弟：旧 USER 及其回答子节点不动，切回旧版本时仍可沿树找回
        messageService.switchSiblingActive(ctx.getSessionId(), parentAssistant,
                AiKnowledgeMessage.ROLE_USER, seq, userMessageId);

        AiKnowledgeMessage assistant = buildAssistant(ctx, userMessageId, 1, seq + 1);
        Long assistantId = messageService.createKnowledgeMessage(assistant);

        sessionService.advanceLeaf(ctx.getSessionId(), assistantId, ctx.getQuestion(), false);

        ctx.setUserMessageId(userMessageId);
        ctx.setAssistantMessageId(assistantId);
        ctx.setAssistantVersion(1);
        return assistantId;
    }

    // ==================== 辅助 ====================

    /** 会话行加锁 + 归属校验（本人 + 知识库不可切换），越权一律按「不存在」处理 */
    private AiKnowledgeSession lockAndVerify(KnowledgeChatContext ctx) {
        AiKnowledgeSession session = sessionService.lockSession(ctx.getSessionId());
        if (session == null) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        if (ctx.getUserId() != null && !ctx.getUserId().equals(session.getUserId())) {
            throw exception(KNOWLEDGE_CHAT_SESSION_NOT_EXISTS);
        }
        if (ctx.getBaseId() != null && !ctx.getBaseId().equals(session.getBaseId())) {
            throw exception(KNOWLEDGE_CHAT_SESSION_BASE_MISMATCH);
        }
        return session;
    }

    /** 同 parent + role 下的最大 version + 1（唯一索引兜底） */
    private int nextVersion(Long parentId, String role) {
        if (parentId == null || parentId == 0L) {
            return 1;
        }
        int max = 0;
        for (AiKnowledgeMessage s : messageService.listByParent(parentId)) {
            if (role.equals(s.getRole()) && s.getVersion() != null && s.getVersion() > max) {
                max = s.getVersion();
            }
        }
        return max + 1;
    }

    /** 下一条展示序号：会话内 MAX(seq) + 1（空会话从 1 开始）；已在会话行锁内执行 */
    private int nextSeq(Long sessionId) {
        Integer max = messageService.selectMaxSeq(sessionId);
        return (max == null ? 0 : max) + 1;
    }

    private AiKnowledgeMessage buildBase(KnowledgeChatContext ctx) {
        AiKnowledgeMessage m = new AiKnowledgeMessage();
        m.setSessionId(ctx.getSessionId());
        m.setUserId(ctx.getUserId());
        m.setTraceId(ctx.getTraceId());
        m.setIsActive(1);
        m.setRating(AiKnowledgeMessage.RATING_NONE);
        return m;
    }

    /** ASSISTANT 消息组装（rootMessageId 默认等于 parent USER id，REGENERATE 可显式传旧 root） */
    private AiKnowledgeMessage buildAssistant(KnowledgeChatContext ctx, Long parentUser, int version, int seq) {
        return buildAssistant(ctx, parentUser, version, seq, parentUser);
    }

    private AiKnowledgeMessage buildAssistant(KnowledgeChatContext ctx, Long parentUser, int version,
                                              int seq, Long rootMessageId) {
        AiKnowledgeMessage assistant = buildBase(ctx);
        assistant.setRole(AiKnowledgeMessage.ROLE_ASSISTANT);
        assistant.setParentId(parentUser);
        assistant.setRootMessageId(rootMessageId);
        assistant.setVersion(version);
        assistant.setSeq(seq);
        assistant.setQuestion(ctx.getQuestion());
        assistant.setAnswer(buildAnswer(ctx));
        assistant.setReferencesJson(ctx.getReferencesJson());
        assistant.setPromptVersionId(ctx.getPromptVersionId());
        assistant.setModelId(ctx.getModelId());
        assistant.setModelConfigId(ctx.getModelConfigId());
        assistant.setRecallMode(ctx.getRecallResult() == null ? null : ctx.getRecallResult().getMode());
        assistant.setRecallDegraded(ctx.isRecallDegraded() ? 1 : 0);
        assistant.setRetrievedCount(ctx.retrievedCount());
        assistant.setCitationDroppedCount(ctx.getCitationDroppedCount() == null ? 0 : ctx.getCitationDroppedCount());
        assistant.setLatencyMs(ctx.elapsedMs());
        assistant.setStatus(statusOf(ctx));
        assistant.setErrorCode(ctx.getErrorCode() == null ? null : String.valueOf(ctx.getErrorCode()));
        assistant.setErrorMsg(clip(ctx.getErrorMsg(), 1000));
        return assistant;
    }

    /**
     * 落库用的回答文本：优先引用校验后的回答，其次错误原因，最后用终态兜底。
     * 保证 ASSISTANT 消息的 answer 永不为空，前端不必为「有记录但无内容」做特殊处理。
     */
    private static String buildAnswer(KnowledgeChatContext ctx) {
        if (StringUtils.hasText(ctx.getAnswer())) {
            return clip(ctx.getAnswer(), 8000);
        }
        if (StringUtils.hasText(ctx.getErrorMsg())) {
            return clip(ctx.getErrorMsg(), 500);
        }
        return "知识库问答结束：" + statusOf(ctx);
    }

    private static String statusOf(KnowledgeChatContext ctx) {
        return StringUtils.hasText(ctx.getTerminalState()) ? ctx.getTerminalState() : "FAILED";
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
