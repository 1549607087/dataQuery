package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 问数对话消息 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId。按主键查询统一走 selectList + LIMIT 1，
 * 避开 MP 3.5.17 cursor 与 Druid PSCache 冲突导致的 500。
 */
public interface AiQueryMessageService extends IService<AiQueryMessage> {

    /** 按主键查询未删除记录 */
    AiQueryMessage getAiQueryMessage(Long id);

    Long createAiQueryMessage(AiQueryMessage entity);

    void updateAiQueryMessage(AiQueryMessage entity);

    /** 逻辑删除单条消息 */
    void deleteAiQueryMessage(Long id);

    /** 会话主链：is_active=1 且未删除，按 seq 升序 */
    List<AiQueryMessage> listActiveBySession(Long sessionId);

    /** 同父消息的全部版本（含历史），按 version 升序 */
    List<AiQueryMessage> listByParent(Long parentId);

    /** 会话下全部未删除消息（用于级联软删） */
    List<AiQueryMessage> listBySession(Long sessionId);

    /** 批量把消息置为非主链（重新生成 / 修改问题时作废旧版本） */
    void deactivate(List<Long> ids);

    /**
     * 作废会话内 seq >= fromSeq 的全部主链消息（分支截断）。
     * 在中间某轮编辑 / 重新生成时调用：基于旧答案的后续轮次退出主链，
     * 仍保留在库中（is_active=0）供版本追溯，但不再出现在会话主链。
     */
    void deactivateFromSeq(Long sessionId, int fromSeq);

    /** 事务内落一条问答消息对（USER + ASSISTANT），返回 ASSISTANT 消息 id */
    Long createQuestionAndAnswer(AiQueryMessage question, AiQueryMessage answer);

    // ==================== 对话树模型（阶段 1）====================
    // 以下方法均只在当前租户 + 指定会话内生效；原生 SQL 显式带 tenant_id 硬过滤，
    // TenantDatabaseInterceptor 会再追加一次形成双保险。调用方不得传入 tenantId。

    /**
     * 锁定一条消息（SELECT ... FOR UPDATE），用于串行化「同一父节点下的兄弟版本创建」。
     * 必须在事务内调用；返回锁定行，不存在返回 null。
     */
    AiQueryMessage lockMessage(Long messageId);

    /**
     * 从指定叶子沿 parent_id 向上回溯完整激活路径，返回按 create_time、id 升序
     * （根 → 叶）的消息列表，即模型读上下文的顺序。
     * 只依赖 current_leaf_id + parent_id，不按 is_active 过滤（起点已是激活末端）。
     */
    List<AiQueryMessage> findActivePath(Long sessionId, Long leafId);

    /**
     * 从指定节点向下沿 is_active=1 找分支末端叶子 id（切换版本后推进 current_leaf_id 用）。
     * 找不到子节点时返回 targetId 本身。
     */
    Long findActiveLeaf(Long sessionId, Long targetId);

    /**
     * 兄弟组内切换选中：把同 (parent_id, role, seq) 的兄弟全部置 is_active=0，
     * 仅 targetId 置 1。只改兄弟组内两三条，不触碰子节点（切回旧分支仍可达末端）。
     *
     * seq 必须参与兄弟组判定的原因：本项目存量是扁平模型，多个轮次的 USER 都可能
     * parent_id=NULL，仅按 (parent_id, role) 会误作废其它轮次。正确树模型下重新生成/
     * 修改问题产生的兄弟本就共用源消息 seq，加 seq 不违背语义，且对存量数据安全。
     */
    void switchSiblingActive(Long sessionId, Long parentId, String role, Integer seq, Long targetId);
}
