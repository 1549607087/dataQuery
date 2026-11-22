package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeChunkHit;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_knowledge_chunk(知识库切片：带向量与引用定位信息，pgvector 检索的最小单位，租户私有)】的数据库操作Mapper
* @createDate 2026-11-17 19:31:58
* @Entity cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk
*
* <p>写入侧由 KnowledgeIngestService 走 BaseMapper；检索侧（P9.2 问答）用下面两条 @Select。
*
* <p>【安全红线】两条检索 SQL 都显式带 tenant_id = #{tenantId} 硬过滤，tenantId 只由 Service
* 从 TenantContextHolder 取，绝不接受前端传参；TenantDatabaseInterceptor 会再自动追加一次，
* 形成双保险。均叠加 base_id 过滤，且只召回 sync_status=1（已构建）且 status=0（正常）的切片。
*
* <p>【向量路】沿用 Schema 召回的「B-tree 预过滤 + 精确距离」方案，不依赖 pgvector 迭代扫描；
* embedding_model_code 由 Service 传入（向量可用时必定非空），用于隔离换模型重建期间的旧向量。
*
 * <p>【关键词路】本地 PG 未装 pg_trgm，content/file_name 的 ILIKE 前导通配符用不上 B-tree
 * 索引，只能限定在 (tenant_id, base_id) 范围内顺序扫描。为把扫描成本压到最低，这里把
 * 「N 个关键词各来一遍 ILIKE」改成两级：
 * <ol>
 *   <li><b>筛</b>：一次正则 {@code (...)~*} 命中任一关键词即通过 —— 每行只过一遍正则引擎，
 *       绝大多数不相关切片在这一步被排除；</li>
 *   <li><b>打分</b>：仅对已通过筛选的行，再逐关键词累加 CASE 权重（正文 1.0 / 文件名 0.5），
 *       保持与原 ILIKE 版本完全一致的排序口径。</li>
 * </ol>
 * 筛选与打分都放在同一个 CTE 上，WHERE 的大正则只求值一次，不随 SELECT 表达式重复执行。
 *
 * <p>调用方必须传入<b>已转义</b>的正则字面量（{@code patterns}），并把它们用 {@code |} 拼成
 * {@code anyRegex}；本方法不对入参做任何转义，直接交给 PG 正则引擎。
 */
public interface AiKnowledgeChunkMapper extends BaseMapper<AiKnowledgeChunk> {

    /**
     * 向量路：余弦距离升序 + tenant/base/model/状态硬过滤。
     *
     * CAST(#{queryVector} AS vector) 用 setString 绑定，不写 embedding <=> #{queryVector} 裸参数，
     * 避免 PG 把参数推断成 character varying。
     */
    @Select("""
            <script>
            SELECT id, base_id, document_id, file_name, chunk_index, content, token_count,
                   page_no, heading_path, char_start, char_end, doc_key, version,
                   (embedding &lt;=> CAST(#{queryVector} AS vector)) AS distance,
                   NULL AS keyword_score
              FROM ai_knowledge_chunk
             WHERE tenant_id = #{tenantId}
               AND base_id = #{baseId}
               AND embedding_model_code = #{modelCode}
               AND sync_status = 1 AND status = 0 AND deleted = 0
               AND embedding IS NOT NULL
             <choose>
               <when test='version != null and version > 0'>
               AND (doc_key, version) IN (
                     SELECT doc_key, MAX(version)
                       FROM ai_knowledge_chunk
                      WHERE tenant_id = #{tenantId} AND base_id = #{baseId}
                        AND version &lt;= #{version} AND deleted = 0
                        AND sync_status = 1 AND status = 0
                      GROUP BY doc_key)
               </when>
               <otherwise>
               AND is_latest = 1
               </otherwise>
             </choose>
             ORDER BY distance ASC
             LIMIT #{limit}
            </script>
            """)
    @Results(value = {
            @Result(column = "id", property = "id"),
            @Result(column = "base_id", property = "baseId"),
            @Result(column = "document_id", property = "documentId"),
            @Result(column = "file_name", property = "fileName"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "content", property = "content"),
            @Result(column = "token_count", property = "tokenCount"),
            @Result(column = "page_no", property = "pageNo"),
            @Result(column = "heading_path", property = "headingPath"),
            @Result(column = "char_start", property = "charStart"),
            @Result(column = "char_end", property = "charEnd"),
            @Result(column = "doc_key", property = "docKey"),
            @Result(column = "version", property = "version"),
            @Result(column = "distance", property = "distance"),
            @Result(column = "keyword_score", property = "keywordScore")
    })
    List<KnowledgeChunkHit> searchByVector(@Param("tenantId") Long tenantId,
                                           @Param("baseId") Long baseId,
                                           @Param("modelCode") String modelCode,
                                           @Param("queryVector") String queryVector,
                                           @Param("version") Integer version,
                                           @Param("limit") int limit);

    /**
     * 关键词路：向量不可用时的兜底，或向量召回不自信时的补充，与向量路共同参与 RRF 融合。
     *
     * <p>不按 embedding_model_code 过滤 —— 换模型重建期间旧向量不可用，但文本关键词仍可召回。
     *
     * @param patterns 已做单字符级正则转义的关键词，例如 {@code a.b} 传入 {@code a\.b}；
     *                 中文关键词不含元字符，通常与原文一致。长度至少 1，由 Service 保证
     * @param anyRegex 上面各 pattern 用 {@code |} 拼成的合并正则，用于一次筛掉不相关行
     */
    @Select("""
            <script>
            WITH matched AS (
                SELECT id, base_id, document_id, file_name, chunk_index, content, token_count,
                       page_no, heading_path, char_start, char_end, doc_key, version
                  FROM ai_knowledge_chunk
                 WHERE tenant_id = #{tenantId}
                   AND base_id = #{baseId}
                   AND sync_status = 1 AND status = 0 AND deleted = 0
                 <choose>
                   <when test='version != null and version > 0'>
                   AND (doc_key, version) IN (
                         SELECT doc_key, MAX(version)
                           FROM ai_knowledge_chunk
                          WHERE tenant_id = #{tenantId} AND base_id = #{baseId}
                            AND version &lt;= #{version} AND deleted = 0
                            AND sync_status = 1 AND status = 0
                          GROUP BY doc_key)
                   </when>
                   <otherwise>
                   AND is_latest = 1
                   </otherwise>
                 </choose>
                   AND (content ~* #{anyRegex} OR file_name ~* #{anyRegex})
            )
            SELECT m.id, m.base_id, m.document_id, m.file_name, m.chunk_index, m.content, m.token_count,
                   m.page_no, m.heading_path, m.char_start, m.char_end, m.doc_key, m.version,
                   NULL AS distance,
                   (<foreach collection='patterns' item='p' separator=' + '>
                       (CASE WHEN m.content ~* #{p} THEN 1.0 ELSE 0.0 END
                      + CASE WHEN m.file_name ~* #{p} THEN 0.5 ELSE 0.0 END)
                    </foreach>) AS keyword_score
              FROM matched m
             ORDER BY keyword_score DESC, m.id ASC
             LIMIT #{limit}
            </script>
            """)
    @Results(value = {
            @Result(column = "id", property = "id"),
            @Result(column = "base_id", property = "baseId"),
            @Result(column = "document_id", property = "documentId"),
            @Result(column = "file_name", property = "fileName"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "content", property = "content"),
            @Result(column = "token_count", property = "tokenCount"),
            @Result(column = "page_no", property = "pageNo"),
            @Result(column = "heading_path", property = "headingPath"),
            @Result(column = "char_start", property = "charStart"),
            @Result(column = "char_end", property = "charEnd"),
            @Result(column = "doc_key", property = "docKey"),
            @Result(column = "version", property = "version"),
            @Result(column = "distance", property = "distance"),
            @Result(column = "keyword_score", property = "keywordScore")
    })
    List<KnowledgeChunkHit> searchByKeyword(@Param("tenantId") Long tenantId,
                                            @Param("baseId") Long baseId,
                                            @Param("patterns") List<String> patterns,
                                            @Param("anyRegex") String anyRegex,
                                            @Param("version") Integer version,
                                            @Param("limit") int limit);

}
