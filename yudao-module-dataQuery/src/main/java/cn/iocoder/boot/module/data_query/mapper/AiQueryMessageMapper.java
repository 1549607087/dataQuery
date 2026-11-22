package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 问数对话消息 Mapper
 *
 * 由 DataQueryAutoConfiguration 的 @MapperScan 统一注册，无需 @Mapper 注解。
 *
 * 【对话树模型 · 阶段 1】下面 4 条原生 SQL 服务于 parent_id / is_active /
 * current_leaf_id 三件套。安全约定（与 AiSchemaVectorMapper 一致）：
 * - 每条 SQL 都显式带 tenant_id = #{tenantId} 硬过滤，tenantId 只由 Service 从
 *   TenantContextHolder 取，绝不接受前端传参；
 * - TenantDatabaseInterceptor 会再自动追加一次 tenant_id（LongValue 字面量），
 *   形成双保险 —— 递归 CTE 的内外两层都显式写，避免拦截器改写行为不确定时漏过滤；
 * - 所有语句再叠加 session_id 条件，杜绝跨会话串数据。
 *
 * 注意：PostgreSQL 的 FOR UPDATE 必须在事务内执行，由 Service 层用 @Transactional 保证。
 */
public interface AiQueryMessageMapper extends BaseMapper<AiQueryMessage> {

    /** 锁定单条消息（事务内 FOR UPDATE），串行化同一父节点下的兄弟版本创建 */
    @Select("""
            SELECT * FROM ai_query_message
             WHERE id = #{messageId}
               AND tenant_id = #{tenantId}
               AND deleted = 0
             FOR UPDATE
            """)
    AiQueryMessage lockMessage(@Param("tenantId") Long tenantId,
                               @Param("messageId") Long messageId);

    /**
     * 从叶子沿 parent_id 向上回溯激活路径。
     * 锚点是叶子；每轮 m.id = path.parent_id 向上找父；parent_id IS NULL 时 JOIN 不匹配自然终止。
     * 返回按 create_time、id 升序（根 → 叶），即模型读上下文的顺序。
     * 递归层与锚点层都显式带 tenant_id + session_id。
     */
    @Select("""
            WITH RECURSIVE path AS (
                SELECT id, session_id, parent_id, role, root_message_id, version, seq,
                       is_active, question, answer, generated_sql, executed_sql,
                       result_json, chart_spec_json, explain, status, error_code, error_msg,
                       trace_id, audit_id, datasource_id, rating, tenant_id, user_id,
                       creator, create_time, updater, update_time, deleted
                  FROM ai_query_message
                 WHERE id = #{leafId}
                   AND session_id = #{sessionId}
                   AND tenant_id = #{tenantId}
                   AND deleted = 0
                UNION ALL
                SELECT m.id, m.session_id, m.parent_id, m.role, m.root_message_id, m.version, m.seq,
                       m.is_active, m.question, m.answer, m.generated_sql, m.executed_sql,
                       m.result_json, m.chart_spec_json, m.explain, m.status, m.error_code, m.error_msg,
                       m.trace_id, m.audit_id, m.datasource_id, m.rating, m.tenant_id, m.user_id,
                       m.creator, m.create_time, m.updater, m.update_time, m.deleted
                  FROM ai_query_message m
                  INNER JOIN path p ON m.id = p.parent_id
                 WHERE m.session_id = #{sessionId}
                   AND m.tenant_id = #{tenantId}
                   AND m.deleted = 0
            )
            SELECT * FROM path
             ORDER BY create_time ASC, id ASC
            """)
    List<AiQueryMessage> findActivePath(@Param("tenantId") Long tenantId,
                                        @Param("sessionId") Long sessionId,
                                        @Param("leafId") Long leafId);

    /**
     * 从目标节点向下沿 is_active=1 找分支末端叶子。
     * 锚点是 target 自身；每轮 m.parent_id = branch.id 向下找激活子节点；
     * 无激活子节点时终止。COALESCE 取叶子：按 create_time+id 倒序的最大深度节点。
     * 正常情况下激活分支是单链，取 MAX(create_time) 即可定位末端。
     */
    @Select("""
            WITH RECURSIVE branch AS (
                SELECT id, parent_id, create_time
                  FROM ai_query_message
                 WHERE id = #{targetId}
                   AND session_id = #{sessionId}
                   AND tenant_id = #{tenantId}
                   AND deleted = 0
                UNION ALL
                SELECT m.id, m.parent_id, m.create_time
                  FROM ai_query_message m
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
     * seq 参与判定可隔离「存量扁平模型下多个 parent=NULL 的不同轮次 USER」，避免误伤。
     * 只改兄弟组自身，不递归改子节点 —— 切回旧分支时可沿 is_active 到达旧末端。
     * 与 activateSibling 由 Service 在同一事务内串联调用。
     */
    @Update("""
            UPDATE ai_query_message
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
            UPDATE ai_query_message
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
