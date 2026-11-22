package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import lombok.Data;

import java.util.List;

/**
 * 知识库问答会话门面（P9.2）
 *
 * <p>把「会话归属校验 + 主链回溯 + 历史拼装」收敛到一处，编排器与 Controller 都只调本接口：
 * <ul>
 *   <li>归属校验：任何按 sessionId 的操作都先校验 {@code user_id == 登录用户}，
 *       越权一律按「会话不存在」处理，不泄露存在性；</li>
 *   <li>主链回溯：current_leaf_id 沿 parent_id 向上取路径，取不到（存量异常 / 空会话）
 *       降级为「全部 is_active=1 的消息」；</li>
 *   <li>历史拼装：只取主链、只取最近 N 条、按 Token 预算从旧到新裁剪。</li>
 * </ul>
 *
 * <p>写入不在这里：对话树落库由 {@code KnowledgeMessageTreeWriter} 事务方法负责。
 */
public interface KnowledgeConversationService {

    /** 按主键查询并校验归属；不存在或非本人抛 KNOWLEDGE_CHAT_SESSION_NOT_EXISTS */
    AiKnowledgeSession requireSession(Long sessionId);

    /** 会话分页：强制按当前登录用户过滤，baseId 可选 */
    PageResult<AiKnowledgeSession> pageSession(Long baseId, Integer pageNo, Integer pageSize);

    /** 重命名会话（标题去空白、截断 100 字） */
    void renameSession(Long sessionId, String title);

    /** 删除会话：同事务内软删会话与全部消息 */
    void deleteSession(Long sessionId);

    /** 会话主链消息（根 → 叶），用于对话页渲染 */
    List<AiKnowledgeMessage> listMessages(Long sessionId);

    /** 按消息 id 查询并校验归属（含其会话属于当前用户）；不存在 / 越权抛 KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND */
    AiKnowledgeMessage requireOwnedMessage(Long messageId);

    /** 某轮回答的全部版本（同 parent + role 的兄弟，含历史） */
    List<AiKnowledgeMessage> listVersions(Long messageId);

    /** 某一轮问题的全部版本（同 seq 下的 USER + ASSISTANT，含历史） */
    List<AiKnowledgeMessage> listQuestionVersions(Long sessionId, Integer seq);

    /** 切换版本：校验目标消息属于该会话后，改兄弟选中并推进 current_leaf_id */
    void switchVersion(Long sessionId, Long targetMessageId);

    /** 赞踩：幂等更新 rating，仅 ASSISTANT 消息可评分 */
    void rate(Long assistantMessageId, Integer rating);

    /**
     * 拼装 RAG 提示词用的历史文本（不含当前轮问题）。
     *
     * @param sessionId   会话编号
     * @param maxMessages 最多纳入的消息条数（USER + ASSISTANT 合计）
     * @param tokenBudget 字符近似 Token 预算，超出从最旧的消息开始丢弃
     */
    HistoryResult buildHistory(Long sessionId, int maxMessages, int tokenBudget);

    /** 历史拼装结果 */
    @Data
    class HistoryResult {
        /** 「用户：…\n助手：…」拼接文本，无历史为空串 */
        private String text = "";
        /** 实际纳入的消息条数 */
        private int messageCount;
        /** 实际纳入的轮次数（一条 USER + 一条 ASSISTANT 记 1 轮） */
        private int turnCount;

        public boolean isEmpty() {
            return text == null || text.isEmpty();
        }
    }
}
