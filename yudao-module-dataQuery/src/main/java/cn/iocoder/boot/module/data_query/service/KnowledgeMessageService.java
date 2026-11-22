package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 知识库问答消息 Service
 *
 * <p>租户隔离依赖 TenantDatabaseInterceptor 追加 tenant_id；下面透传的原生 SQL
 * 方法（findActivePath / findActiveLeaf / lockMessage / switchSiblingActive）
 * 由 Mapper 显式带 tenant_id 与 session_id 双过滤。
 */
public interface KnowledgeMessageService extends IService<AiKnowledgeMessage> {

    AiKnowledgeMessage getKnowledgeMessage(Long id);

    Long createKnowledgeMessage(AiKnowledgeMessage entity);

    void updateKnowledgeMessage(AiKnowledgeMessage entity);

    void deleteKnowledgeMessage(Long id);

    /** 软删某会话下的全部消息（单条 UPDATE，避免逐条循环的 N+1） */
    void deleteBySession(Long sessionId);

    List<AiKnowledgeMessage> listBySession(Long sessionId);

    List<AiKnowledgeMessage> listByParent(Long parentId);

    List<AiKnowledgeMessage> listActiveBySession(Long sessionId);

    /** 会话内最大展示序号，空会话返回 0 */
    Integer selectMaxSeq(Long sessionId);

    AiKnowledgeMessage lockMessage(Long messageId);

    /** 从叶子沿 parent_id 向上回溯主链（根 → 叶） */
    List<AiKnowledgeMessage> findActivePath(Long sessionId, Long leafId);

    /** 从目标节点向下沿 is_active=1 找分支末端叶子 */
    Long findActiveLeaf(Long sessionId, Long targetId);

    /** 兄弟组切换：同 (parent, role, seq) 仅 target 置 is_active=1（下一轮版本切换使用） */
    void switchSiblingActive(Long sessionId, Long parentId, String role, Integer seq, Long targetId);
}
