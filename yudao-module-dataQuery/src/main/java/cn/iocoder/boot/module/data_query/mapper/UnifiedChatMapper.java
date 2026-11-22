package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedSessionRespVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统一对话会话聚合 Mapper。
 *
 * <p>把 ai_query_session 与 ai_knowledge_session 两张表的会话用 UNION ALL 聚合为一个列表。
 * 不建物理视图、不动原表：两表归属链互斥且已有索引，聚合只在「我的会话」入口发生。
 *
 * <p>安全约定：
 * <ul>
 *   <li>tenant_id / user_id 全部由服务端从登录态与 TenantContextHolder 取，不接受前端传参；</li>
 *   <li>每张表各自带 deleted=0 过滤，避免软删会话串入列表；</li>
 *   <li>mode 以字符串常量区分，id 与 resourceId 分别取自两表对应列。</li>
 * </ul>
 *
 * <p>继承 BaseMapper 仅为对齐项目 MapperScan 约定；本 Mapper 无主实体，泛型用 Object。
 */
public interface UnifiedChatMapper extends BaseMapper<Object> {

    /**
     * 聚合分页查询。返回未分页的完整 VO 列表，由调用方配合 Page 做内存分页
     * （UNION ALL + 动态排序用 MyBatis-Plus 分页插件对子查询支持不稳定，改为显式 LIMIT）。
     */
    @Select("""
            <script>
            SELECT * FROM (
                SELECT 'QUERY' AS mode, s.id AS sessionId, s.title, s.last_question AS lastQuestion,
                       s.last_ask_time AS lastAskTime, s.message_count AS messageCount,
                       s.status, s.trace_id AS traceId, s.datasource_id AS resourceId,
                       s.create_time AS createTime
                  FROM ai_query_session s
                 WHERE s.deleted = 0 AND s.user_id = #{userId} AND s.tenant_id = #{tenantId}
                   <if test="mode != null and mode != ''">
                   AND #{mode} = 'QUERY'
                   </if>
                   <if test="title != null and title != ''">
                   AND s.title ILIKE CONCAT('%', #{title}, '%')
                   </if>
                UNION ALL
                SELECT 'KNOWLEDGE' AS mode, k.id AS sessionId, k.title, k.last_question AS lastQuestion,
                       k.last_ask_time AS lastAskTime, k.message_count AS messageCount,
                       k.status, k.trace_id AS traceId, k.base_id AS resourceId,
                       k.create_time AS createTime
                  FROM ai_knowledge_session k
                 WHERE k.deleted = 0 AND k.user_id = #{userId} AND k.tenant_id = #{tenantId}
                   <if test="mode != null and mode != ''">
                   AND #{mode} = 'KNOWLEDGE'
                   </if>
                   <if test="title != null and title != ''">
                   AND k.title ILIKE CONCAT('%', #{title}, '%')
                   </if>
            ) t
            ORDER BY t.lastAskTime DESC NULLS LAST, t.sessionId DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<UnifiedSessionRespVO> selectSessionPage(@Param("userId") Long userId,
                                                  @Param("tenantId") Long tenantId,
                                                  @Param("mode") String mode,
                                                  @Param("title") String title,
                                                  @Param("limit") Long limit,
                                                  @Param("offset") Long offset);

    /** 聚合会话总数（与 selectSessionPage 同过滤条件） */
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT s.id FROM ai_query_session s
                 WHERE s.deleted = 0 AND s.user_id = #{userId} AND s.tenant_id = #{tenantId}
                   <if test="mode != null and mode != ''">
                   AND #{mode} = 'QUERY'
                   </if>
                   <if test="title != null and title != ''">
                   AND s.title ILIKE CONCAT('%', #{title}, '%')
                   </if>
                UNION ALL
                SELECT k.id FROM ai_knowledge_session k
                 WHERE k.deleted = 0 AND k.user_id = #{userId} AND k.tenant_id = #{tenantId}
                   <if test="mode != null and mode != ''">
                   AND #{mode} = 'KNOWLEDGE'
                   </if>
                   <if test="title != null and title != ''">
                   AND k.title ILIKE CONCAT('%', #{title}, '%')
                   </if>
            ) t
            </script>
            """)
    Long countSessions(@Param("userId") Long userId,
                       @Param("tenantId") Long tenantId,
                       @Param("mode") String mode,
                       @Param("title") String title);
}
