package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * 知识库问答会话 Service
 *
 * <p>全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId。按主键查询统一走 selectList + LIMIT 1，
 * 避开 MP 3.17 cursor 与 Druid PSCache 冲突导致的 500（与 AiQuerySessionService 同口径）。
 */
public interface KnowledgeSessionService extends IService<AiKnowledgeSession> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiKnowledgeSession getKnowledgeSession(Long id);

    /** 新建会话；title 取问题前 30 字，sessionNo 自动生成 */
    AiKnowledgeSession createSession(Long baseId, String question, String traceId);

    Long createKnowledgeSession(AiKnowledgeSession entity);

    void updateKnowledgeSession(AiKnowledgeSession entity);

    void deleteKnowledgeSession(Long id);

    /** 事务内锁定会话行（FOR UPDATE），串行化 seq 计算与叶子推进 */
    AiKnowledgeSession lockSession(Long sessionId);

    /**
     * 推进当前激活分支末端指针（对话树模型）。
     *
     * @param countTurn true 表示这是一次新的提问轮次，message_count 原子 +2；
     *                  重新生成 / 修改问题 / 切换版本传 false（本轮不涉及，接口预留）
     */
    void advanceLeaf(Long sessionId, Long leafId, String lastQuestion, boolean countTurn);
}
