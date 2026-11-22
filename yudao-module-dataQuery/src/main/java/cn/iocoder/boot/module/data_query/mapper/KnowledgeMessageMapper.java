package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库问答消息 Mapper（对话树）
 *
 * <p>由 DataQueryAutoConfiguration 的 @MapperScan 统一注册，无需 @Mapper 注解。
 *
 * <p>对话树三件套：parent_id / is_active / current_leaf_id。安全约定与 AiQueryMessageMapper 一致：
 * <ul>
 *   <li>每条 SQL 显式 tenant_id = #{tenantId} 硬过滤，tenantId 只由 Service 从
 *       TenantContextHolder 取，绝不接受前端传参；</li>
 *   <li>递归 CTE 的内外两层都显式写 tenant_id，避免拦截器改写行为不确定时漏过滤；</li>
 *   <li>所有语句再叠加 session_id 条件，杜绝跨会话串数据。</li>
 * </ul>
 * 注意：PostgreSQL 的 FOR UPDATE 必须在事务内执行，由 Service 层用 @Transactional 保证。
 */
public interface KnowledgeMessageMapper extends BaseMapper<AiKnowledgeMessage> {

    /** 锁定单条消息（事务内 FOR UPDATE），串行化同一父节点下的兄弟版本创建 */
    @Select("""
            SELECT * FROM ai_knowledge_message
             WHERE id = #{messageId}
               AND tenant_id = #{tenantId}
               AND deleted = 0
             FOR UPDATE
            """)
    AiKnowledgeMessage lockMessage(@Param("tenantId") Long tenantId,
                                   @Param("messageId") Long messageId);

    /** 会话内最大展示序号；空会话返回 0（COALESCE 保证不为 null） */
    @Select("""
            SELECT COALESCE(MAX(seq), 0) FROM ai_knowledge_message
             WHERE tenant_id = #{tenantId}
               AND session_id = #{sessionId}
               AND deleted = 0
            """)
    Integer selectMaxSeq(@Param("tenantId") Long tenantId,
                         @Param("sessionId") Long sessionId);

    /**
     * 从叶子沿 parent_id 向上回溯激活路径。
     * 锚点是叶子；每轮 m.id = path.parent_id 向上找父；parent_id IS NULL 时 JOIN 不匹配自然终止。
     * 返回按 create_time、id 升序（根 → 叶），即模型读上下文的顺序。
     */
    @Select("""
            WITH RECURSIVE path AS (
                SELECT id, session_id, role, parent_id, root_message_id, version, seq, is_active,
                       question, answer, references_json, prompt_version_id, model_id, model_config_id,
                       recall_mode, recall_degraded, retrieved_count, citation_dropped_count, latency_ms,
                       status, error_code, error_msg, trace_id, rating, tenant_id, user_id,
                       creator, create_time, updater, update_time, deleted
                  FROM ai_knowledge_message
                 WHERE id = #{leafId}
                   AND session_id = #{sessionId}
                   AND tenant_id = #{tenantId}
                   AND deleted = 0
                UNION ALL
                SELECT m.id, m.session_id, m.role, m.parent_id, m.root_message_id, m.version, m.seq, m.is_active,
                       m.question, m.answer, m.references_json, m.prompt_version_id, m.model_id, m.model_config_id,
                       m.recall_mode, m.recall_degraded, m.retrieved_count, m.citation_dropped_count, m.latency_ms,
                       m.status, m.error_code, m.error_msg, m.trace_id, m.rating, m.tenant_id, m.user_id,
                       m.creator, m.create_time, m.updater, m.update_time, m.deleted
                  FROM ai_knowledge_message m
                  INNER JOIN path p ON m.id = p.parent_id
                 WHERE m.session_id = #{sessionId}
                   AND m.tenant_id = #{tenantId}
                   AND m.deleted = 0
            )
            SELECT * FROM path
             ORDER BY create_time ASC, id ASC
            """)
    List<AiKnowledgeMessage> findActivePath(@Param("tenantId") Long tenantId,
                                            @Param("sessionId") Long sessionId,
                                            @Param("leafId") Long leafId);

    /**
     * 从目标节点向下沿 is_active=1 找分支末端叶子。
     * 锚点是 target 自身；每轮 m.parent_id = branch.id 向下找激活子节点；无激活子节点时终止。
     * 取 MAX(create_time) 定位末端（激活分支是单链）。
     */
    @Select("""
            WITH RECURSIVE branch AS (
                SELECT id, parent_id, create_time
                  FROM ai_knowledge_message
                 WHERE id = #{targetId}
                   AND session_id = #{sessionId}
                   AND tenant_id = #{tenantId}
                   AND deleted = 0
                UNION ALL
                SELECT m.id, m.parent_id, m.create_time
                  FROM ai_knowledge_message m
                  INNER JOIN branch b ON m.parent_id = b.id
                 WHERE m.session_id = #{sessionId}
                   AND m.tenant_id = #{tenantId}
                   AND m.deleted = 0
                   AND m.is_active = 1
            )
            SELECT id FROM branch
             ORDER BY create_time DESC, id DESC
             LIMIT 1
            """)
    Long findActiveLeaf(@Param("tenantId") Long tenantId,
                        @Param("sessionId") Long sessionId,
                        @Param("targetId") Long targetId);

    /**
     * 兄弟组去选中：同 (parent_id, role, seq) 的兄弟全部置 is_active=0（不含 target）。
     * parent_id 可能为 NULL（根 USER 兄弟组），故用 IS NOT DISTINCT FROM 做 NULL 安全比较。
     * 只改兄弟组自身，不递归改子节点。
     */
    @Update("""
            UPDATE ai_knowledge_message
               SET is_active = 0, update_time = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId}
               AND session_id = #{sessionId}
               AND parent_id IS NOT DISTINCT FROM #{parentId}
               AND role = #{role}
               AND seq = #{seq}
               AND deleted = 0
               AND id != #{targetId}
            """)
    void deactivateSiblings(@Param("tenantId") Long tenantId,
                            @Param("sessionId") Long sessionId,
                            @Param("parentId") Long parentId,
                            @Param("role") String role,
                            @Param("seq") Integer seq,
                            @Param("targetId") Long targetId);

    /** 兄弟组选中目标：仅把 target 置 is_active=1 */
    @Update("""
            UPDATE ai_knowledge_message
               SET is_active = 1, update_time = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId}
               AND session_id = #{sessionId}
               AND id = #{targetId}
               AND deleted = 0
            """)
    void activateSibling(@Param("tenantId") Long tenantId,
                         @Param("sessionId") Long sessionId,
                         @Param("targetId") Long targetId);
}
