package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.entity.AiQueryAudit;
import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.service.AiQueryAuditService;
import cn.iocoder.boot.module.data_query.service.AiQueryMessageService;
import cn.iocoder.boot.module.data_query.service.AiQuerySessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 会话内追问的轻量规则改写（A 方案，纯规则、零 LLM 调用）。
 *
 * <p>背景：一期 S1 只做空白折叠，S3 召回完全基于当前轮问题文本。会话内第二轮若是
 * 「那按月份呢」「华东的呢」这类指代型追问，问题里没有任何表/列/业务名词，
 * 向量路与关键词路都召不到相关表 → 直接落到 CLARIFY。本服务在 S1 之后、
 * S3 之前补一步：判定为追问时，把上一轮 USER 问题（及可选的上轮命中表）
 * 作为上下文拼进当前问题，得到自包含问题再送召回。</p>
 *
 * <b>安全边界（不可绕过）：</b>
 * <ol>
 *   <li>改写只作用于 {@code ctx.normalizedQuestion}（S3 召回 / S4 提示词的输入），
 *       用户原文 {@code ctx.question} 保持不变，审计 question 字段仍是用户输入原样；</li>
 *   <li>上轮命中表只作为文本信号参与召回，<b>不作为本轮 S6 白名单</b>：
 *       本轮白名单仍由 S3 重新召回 + PG 元数据二次核验生成，改写不放宽任何过滤；</li>
 *   <li>历史消息全部来自 {@code listActiveBySession(sessionId)}，依赖租户拦截器硬过滤，
 *       且排除当前重做轮次（REGENERATE/EDIT 的 sourceMessage 对应 USER），不跨会话、不跨租户；</li>
 *   <li>任一步异常都降级为「不改写」，返回原问题，绝不阻断主链路。</li>
 * </ol>
 *
 * <p>开关：{@code data-query.chat.followup-rewrite-enabled=false} 可立即关闭。</p>
 */
@Slf4j
@Component
public class FollowupRewriteService {

    /** 改写来源标识，写入 SSE 事件与审计扩展，便于观测 */
    public static final String SOURCE = "HISTORY_RULE";

    /** 上轮命中表拼接上限，控制 Embedding 与 prompt 体积 */
    private static final int MAX_TABLE_NAMES = 6;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private AiQueryMessageService messageService;
    @Resource
    private AiQuerySessionService sessionService;
    @Resource
    private AiQueryAuditService auditService;
    @Resource
    private ChatOrchestratorProperties properties;

    /**
     * 尝试把当前问题改写为自包含问题。
     *
     * @param sessionId      会话 id（S0 已建立）
     * @param currentQuestion S1 规范化后的问题
     * @param excludeUserId  当前重做轮次对应的 USER 消息 id（ASK 传 null）；
     *                       排除它可避免 REGENERATE/EDIT 把「自己」当成上一轮
     * @return 改写结果；applied=false 表示未改写，question 即入参原文
     */
    public RewriteResult rewrite(Long sessionId, String currentQuestion, Long excludeUserId) {
        RewriteResult result = new RewriteResult();
        result.setQuestion(currentQuestion);
        result.setApplied(false);
        result.setSource(null);
        result.setPrevQuestion(null);

        if (!Boolean.TRUE.equals(properties.getFollowupRewriteEnabled())
                || sessionId == null || !StringUtils.hasText(currentQuestion)) {
            return result;
        }
        // 非追问不改：完整问题原样送召回，零副作用
        if (!FollowupRewriteRules.isFollowup(currentQuestion)) {
            return result;
        }

        try {
            Context prev = loadPreviousContext(sessionId, excludeUserId);
            if (prev == null || !StringUtils.hasText(prev.getQuestion())) {
                return result;
            }
            String rewritten = FollowupRewriteRules.buildRewritten(
                    currentQuestion, prev.getQuestion(), prev.getTableNames());
            if (!StringUtils.hasText(rewritten) || rewritten.equals(currentQuestion)) {
                return result;
            }
            result.setApplied(true);
            result.setSource(SOURCE);
            result.setPrevQuestion(prev.getQuestion());
            result.setQuestion(rewritten);
            log.info("[rewrite][applied] sessionId={} current={} prev={} rewritten={}",
                    sessionId, currentQuestion, prev.getQuestion(), rewritten);
        } catch (Exception ex) {
            // 改写是增强项：任何异常都降级为不改写，绝不阻断 ask 主链路
            log.warn("[rewrite][skip] sessionId={} reason={}", sessionId, ex.toString());
        }
        return result;
    }

    /**
     * 当前分支消息：优先沿 current_leaf_id 回溯（树模型，切分支后上下文随分支走，设计文档 §6），
     * 存量扁平数据回溯不足 2 条时降级到 is_active + seq 扫描，避免存量追问上下文丢失。
     */
    private List<AiQueryMessage> currentBranch(Long sessionId) {
        AiQuerySession session = sessionService.getAiQuerySession(sessionId);
        Long leafId = session == null ? null : session.getCurrentLeafId();
        if (leafId != null && leafId > 0) {
            List<AiQueryMessage> path = messageService.findActivePath(sessionId, leafId);
            if (path != null && path.size() >= 2) {
                return path;
            }
        }
        return messageService.listActiveBySession(sessionId);
    }

    /** 读取上一轮上下文：当前分支中排除当前重做轮次后，最后一条 USER 及其命中表 */
    private Context loadPreviousContext(Long sessionId, Long excludeUserId) {
        List<AiQueryMessage> active = currentBranch(sessionId);
        if (active == null || active.isEmpty()) {
            return null;
        }
        AiQueryMessage prevUser = null;
        for (int i = active.size() - 1; i >= 0; i--) {
            AiQueryMessage m = active.get(i);
            if (!AiQueryMessage.ROLE_USER.equals(m.getRole())) {
                continue;
            }
            if (excludeUserId != null && excludeUserId.equals(m.getId())) {
                continue;
            }
            prevUser = m;
            break;
        }
        if (prevUser == null) {
            return null;
        }
        Context ctx = new Context();
        ctx.setQuestion(prevUser.getQuestion());

        // 命中表属于增强信号：从上一轮 ASSISTANT 关联的审计 recalled_tables 读取，失败即跳过
        try {
            for (AiQueryMessage m : active) {
                if (AiQueryMessage.ROLE_ASSISTANT.equals(m.getRole())
                        && prevUser.getId().equals(m.getParentId())
                        && m.getAuditId() != null) {
                    AiQueryAudit audit = auditService.getAiQueryAudit(m.getAuditId());
                    if (audit != null && StringUtils.hasText(audit.getRecalledTables())) {
                        List<String> tables = JSON.readValue(audit.getRecalledTables(),
                                new TypeReference<List<String>>() {
                                });
                        if (tables != null) {
                            ctx.setTableNames(tables.stream()
                                    .filter(StringUtils::hasText)
                                    .limit(MAX_TABLE_NAMES)
                                    .toList());
                        }
                    }
                    break;
                }
            }
        } catch (Exception ex) {
            log.debug("[rewrite][tables] 上轮命中表读取失败，仅用问题文本改写: {}", ex.getMessage());
        }
        return ctx;
    }

    // ==================== 结果对象 ====================

    @Data
    public static class RewriteResult {
        /** 改写后的自包含问题；未改写时为入参原文 */
        private String question;
        /** 是否实际改写 */
        private boolean applied;
        /** 改写来源标识（HISTORY_RULE），未改写为 null */
        private String source;
        /** 上一轮问题原文，供 SSE 展示「已理解为」，未改写为 null */
        private String prevQuestion;
    }

    @Data
    private static class Context {
        private String question;
        private List<String> tableNames = Collections.emptyList();
    }
}
