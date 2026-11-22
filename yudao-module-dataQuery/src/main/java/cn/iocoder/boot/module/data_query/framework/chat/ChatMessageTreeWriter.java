package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import cn.iocoder.boot.module.data_query.service.AiQueryMessageService;
import cn.iocoder.boot.module.data_query.service.AiQuerySessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 对话树写入器（阶段 2）：在一次事务内完成「建兄弟 + 推进叶子」的落库编排。
 *
 * <p>为什么独立成 Bean：调用方 {@link ChatMessageRecorder#record} 需要吞异常降级
 * （落库失败不能影响 ask 终态）。若把 @Transactional 放在 Recorder 内部方法上，
 * 同类自调用不走代理、事务失效；且异常被吞掉也不会回滚。这里把事务方法放到独立 Bean，
 * 由 Recorder 通过 Spring 代理调用：方法内抛异常 → 整笔回滚 → 异常继续外抛 →
 * Recorder 的 try-catch 捕获降级，二者互不干扰。
 *
 * <p>树语义（对应 deepseek-web-chat-design.md §5）：
 * <ul>
 *   <li>ASK：新 USER 的 parent = 会话当前 current_leaf_id（首条为 NULL 根），落库后叶子推进到新 ASSISTANT；</li>
 *   <li>REGENERATE：新 ASSISTANT 的 parent = 源 ASSISTANT 的 parent（同一 USER），version 取兄弟最大值 +1，
 *       兄弟切换使旧答案退出选中，叶子推进到新 ASSISTANT；</li>
 *   <li>EDIT：新 USER 的 parent = 源 USER 的 parent（上一条 ASSISTANT；存量扁平数据可能为 NULL 根），
 *       version 取兄弟最大值 +1，兄弟切换使旧问题退出选中，叶子推进到新 ASSISTANT；
 *       旧 USER 及其回答整支保留、不作废，切回旧问题版本时仍可沿树路径找回。</li>
 * </ul>
 *
 * <p>不再使用 deactivateFromSeq 做 seq 截断：分支退出选中由兄弟切换 + 叶子指针表达，
 * 旧分支整支保留可追溯，由 current_leaf_id 决定哪条路径是当前激活路径。
 */
@Slf4j
@Component
public class ChatMessageTreeWriter {

    @Resource
    private AiQueryMessageService messageService;

    @Resource
    private AiQuerySessionService sessionService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 事务入口：按操作类型分派。任何异常都向外抛（由 Recorder 捕获降级），确保回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long write(QueryContext ctx, AiChatAskRespVO resp) {
        String op = StringUtils.hasText(ctx.getMessageOp()) ? ctx.getMessageOp() : ChatMessageRecorder.OP_ASK;
        return switch (op) {
            case ChatMessageRecorder.OP_REGENERATE -> recordRegenerate(ctx, resp);
            case ChatMessageRecorder.OP_EDIT -> recordEdit(ctx, resp);
            default -> recordAsk(ctx, resp);
        };
    }

    // ==================== 三操作 ====================

    /** 普通提问：parent = 当前叶子，新建 USER + ASSISTANT，叶子推进到 ASSISTANT */
    private Long recordAsk(QueryContext ctx, AiChatAskRespVO resp) {
        Long sessionId = ctx.getSessionId();
        int seq = nextSeq(sessionId);
        Long parent = currentLeaf(sessionId); // 首条为 NULL（根）

        AiQueryMessage user = buildBase(ctx);
        user.setRole(AiQueryMessage.ROLE_USER);
        user.setParentId(parent);
        user.setRootMessageId(0L); // 落库后回填自己 id
        user.setVersion(1);
        user.setSeq(seq);
        user.setQuestion(ctx.getQuestion());
        user.setStatus(resp.getStatus());
        Long userId = messageService.createAiQueryMessage(user);
        user.setRootMessageId(userId);
        messageService.updateAiQueryMessage(user);

        AiQueryMessage assistant = buildAssistant(ctx, resp, userId, userId, 1, seq + 1);
        Long assistantId = messageService.createAiQueryMessage(assistant);

        sessionService.advanceLeaf(sessionId, assistantId);
        ctx.setUserMessageId(userId);
        ctx.setAssistantMessageId(assistantId);
        return assistantId;
    }

    /** 重新生成：新 ASSISTANT 与源 ANSWER 同 parent（同一 USER），只切换 ASSISTANT 兄弟 */
    private Long recordRegenerate(QueryContext ctx, AiChatAskRespVO resp) {
        AiQueryMessage old = ctx.getSourceMessage();
        if (old == null || !AiQueryMessage.ROLE_ASSISTANT.equals(old.getRole())) {
            return recordAsk(ctx, resp); // 源缺失/角色不符，降级为普通落库
        }
        Long sessionId = ctx.getSessionId();
        Long parentUser = old.getParentId();
        if (parentUser == null || parentUser == 0L) {
            return recordAsk(ctx, resp); // 存量异常数据：无父 USER，无法建兄弟
        }

        // 锁父 USER，串行化同一问题下的并发重新生成（version MAX+1 竞态兜底）
        messageService.lockMessage(parentUser);

        int seq = old.getSeq() == null ? nextSeq(sessionId) : old.getSeq();
        int version = nextVersion(parentUser, AiQueryMessage.ROLE_ASSISTANT);
        AiQueryMessage assistant = buildAssistant(ctx, resp, parentUser,
                old.getRootMessageId() == null ? parentUser : old.getRootMessageId(), version, seq);
        Long assistantId = messageService.createAiQueryMessage(assistant);

        // 兄弟切换：同 (parent=USER, role=ASSISTANT, seq) 旧答案置 0，新答案置 1
        messageService.switchSiblingActive(sessionId, parentUser, AiQueryMessage.ROLE_ASSISTANT, seq, assistantId);

        sessionService.advanceLeaf(sessionId, assistantId);
        ctx.setUserMessageId(parentUser);
        ctx.setAssistantMessageId(assistantId);
        return assistantId;
    }

    /** 修改问题：新 USER 与源 USER 同 parent（上一条 ASSISTANT），切换 USER 兄弟并推进叶子 */
    private Long recordEdit(QueryContext ctx, AiChatAskRespVO resp) {
        AiQueryMessage oldUser = ctx.getSourceMessage();
        if (oldUser == null || !AiQueryMessage.ROLE_USER.equals(oldUser.getRole())) {
            return recordAsk(ctx, resp);
        }
        Long sessionId = ctx.getSessionId();
        Long parentAssistant = oldUser.getParentId(); // 上一条 ASSISTANT；存量扁平数据可能为 NULL

        // 父节点存在时锁它，串行化同一 ASSISTANT 下的并发「修改问题」
        if (parentAssistant != null && parentAssistant != 0L) {
            messageService.lockMessage(parentAssistant);
        }

        int seq = oldUser.getSeq() == null ? nextSeq(sessionId) : oldUser.getSeq();
        int userVersion = nextVersion(parentAssistant, AiQueryMessage.ROLE_USER);

        AiQueryMessage user = buildBase(ctx);
        user.setRole(AiQueryMessage.ROLE_USER);
        user.setParentId(parentAssistant); // 关键：不再固定 0，指向旧 USER 的 parent
        user.setRootMessageId(0L);
        user.setQuestion(ctx.getQuestion());
        user.setVersion(userVersion);
        user.setSeq(seq);
        user.setStatus(resp.getStatus());
        Long userId = messageService.createAiQueryMessage(user);
        user.setRootMessageId(userId);
        messageService.updateAiQueryMessage(user);

        // 兄弟切换：同 (parent=ASSISTANT, role=USER, seq) 旧问题置 0，新问题置 1。
        // 按设计文档 §5.5/§5.6：只动 U_old、U_new 两条兄弟，绝不作废旧 USER 的回答子节点，
        // 否则切回旧问题版本时向下找叶子会停在 U_old，旧回答就"消失"了。
        // is_active 仅表达"兄弟组内谁被选中"，主链可见性由 current_leaf_id 回溯路径决定。
        messageService.switchSiblingActive(sessionId, parentAssistant, AiQueryMessage.ROLE_USER, seq, userId);

        AiQueryMessage assistant = buildAssistant(ctx, resp, userId, userId, 1, seq + 1);
        Long assistantId = messageService.createAiQueryMessage(assistant);

        sessionService.advanceLeaf(sessionId, assistantId);
        ctx.setUserMessageId(userId);
        ctx.setAssistantMessageId(assistantId);
        return assistantId;
    }

    // ==================== 辅助 ====================

    /** 会话当前激活叶子（NULL 表示尚无消息） */
    private Long currentLeaf(Long sessionId) {
        AiQuerySession session = sessionService.getAiQuerySession(sessionId);
        return session == null ? null : session.getCurrentLeafId();
    }

    /** 同 parent + role 下的最大 version + 1（唯一索引 (tenant,parent,role,version) 兜底） */
    private int nextVersion(Long parentId, String role) {
        if (parentId == null || parentId == 0L) {
            return 1; // 根兄弟组不进唯一索引，version 仅展示用
        }
        int max = 0;
        for (AiQueryMessage s : messageService.listByParent(parentId)) {
            if (role.equals(s.getRole()) && s.getVersion() != null && s.getVersion() > max) {
                max = s.getVersion();
            }
        }
        return max + 1;
    }

    /** 下一条 seq：取会话内最大 seq + 1（空会话从 1 开始）。树模型下 seq 仅作展示排序 */
    private int nextSeq(Long sessionId) {
        int max = 0;
        for (AiQueryMessage m : messageService.listBySession(sessionId)) {
            if (m.getSeq() != null && m.getSeq() > max) {
                max = m.getSeq();
            }
        }
        return max + 1;
    }

    private AiQueryMessage buildAssistant(QueryContext ctx, AiChatAskRespVO resp, Long parentId,
                                          Long rootMessageId, int version, int seq) {
        AiQueryMessage assistant = buildBase(ctx);
        assistant.setRole(AiQueryMessage.ROLE_ASSISTANT);
        assistant.setParentId(parentId);
        assistant.setRootMessageId(rootMessageId);
        assistant.setVersion(version);
        assistant.setSeq(seq);
        assistant.setAnswer(buildAnswer(ctx, resp));
        assistant.setGeneratedSql(ctx.getGeneratedSql());
        assistant.setExecutedSql(ctx.getExecutedSql());
        assistant.setResultJson(toJson(ctx.getQueryResult()));
        assistant.setChartSpecJson(toJson(ctx.getChartSpec()));
        assistant.setExplain(ctx.getExplain());
        assistant.setQuestion(ctx.getQuestion());
        assistant.setStatus(resp.getStatus());
        assistant.setErrorCode(resp.getErrorCode() == null ? null : String.valueOf(resp.getErrorCode()));
        assistant.setErrorMsg(clip(ctx.getErrorMsg(), 1000));
        if (ctx.getAuditId() != null) {
            assistant.setAuditId(ctx.getAuditId());
        } else if (ctx.getQueryResult() != null) {
            // S7 写入审计后 QueryResult 带 auditId，兜底回填
            assistant.setAuditId(extractAuditId(ctx.getQueryResult()));
        }
        return assistant;
    }

    /** 回答文本：优先 S8 摘要，其次口径说明，最后用终态 + 行数做兜底描述 */
    private static String buildAnswer(QueryContext ctx, AiChatAskRespVO resp) {
        if (StringUtils.hasText(ctx.getSummary())) {
            return ctx.getSummary();
        }
        if (StringUtils.hasText(ctx.getExplain())) {
            return ctx.getExplain();
        }
        if ("DONE".equals(resp.getStatus()) && resp.getResult() != null) {
            QueryResult qr = resp.getResult();
            int rows = qr.getRowCount() == null ? (qr.getRows() == null ? 0 : qr.getRows().size()) : qr.getRowCount();
            return "已生成并执行只读查询，返回 " + rows + " 行结果。";
        }
        if (StringUtils.hasText(ctx.getErrorMsg())) {
            return clip(ctx.getErrorMsg(), 500);
        }
        return "问数结束：" + resp.getStatus();
    }

    private AiQueryMessage buildBase(QueryContext ctx) {
        AiQueryMessage m = new AiQueryMessage();
        m.setSessionId(ctx.getSessionId());
        m.setUserId(ctx.getUserId());
        m.setDatasourceId(ctx.getDatasourceId());
        m.setTraceId(ctx.getTraceId());
        m.setIsActive(1);
        m.setRating(AiQueryMessage.RATING_NONE);
        return m;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.debug("[chat][message-record] 序列化失败 type={}, reason={}",
                    value.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
    }

    private static Long extractAuditId(Object queryResult) {
        if (queryResult instanceof QueryResult qr) {
            return qr.getAuditId();
        }
        return null;
    }

    private static String clip(String value, int max) {
        return value == null ? null : (value.length() <= max ? value : value.substring(0, max));
    }
}
