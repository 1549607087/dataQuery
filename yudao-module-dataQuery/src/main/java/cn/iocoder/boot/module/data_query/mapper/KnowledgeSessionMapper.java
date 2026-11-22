package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库问答会话 Mapper
 *
 * <p>由 DataQueryAutoConfiguration 的 @MapperScan 统一注册，无需 @Mapper 注解。
 * 安全约定与 AiQueryMessageMapper 一致：显式 tenant_id 硬过滤，tenantId 只由 Service
 * 从 TenantContextHolder 取；TenantDatabaseInterceptor 会再自动追加一次，形成双保险。
 */
public interface KnowledgeSessionMapper extends BaseMapper<AiKnowledgeSession> {

    /**
     * 锁定会话行（事务内 FOR UPDATE），串行化同一会话的 seq 计算与叶子推进。
     * PostgreSQL 的 FOR UPDATE 必须在事务内执行，由 Service 层用 @Transactional 保证。
     */
    @Select("""
            SELECT * FROM ai_knowledge_session
             WHERE id = #{sessionId}
               AND tenant_id = #{tenantId}
               AND deleted = 0
             FOR UPDATE
            """)
    AiKnowledgeSession lockSession(@Param("tenantId") Long tenantId,
                                   @Param("sessionId") Long sessionId);
}
