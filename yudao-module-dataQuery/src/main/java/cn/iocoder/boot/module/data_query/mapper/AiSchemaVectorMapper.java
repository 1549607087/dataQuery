package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiSchemaVector;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaVectorHit;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Schema 向量索引 Mapper
 *
 * 由 DataQueryAutoConfiguration 的 @MapperScan 统一注册。
 * 写入侧只用 BaseMapper 的 selectList / insert / updateById，embedding 列的读写由
 * PgVectorTypeHandler 处理；检索侧（P3）用下面两条 @Select。
 *
 * 【安全红线】两条检索 SQL 都显式带 tenant_id = #{tenantId} 硬过滤，tenantId 只由
 * Service 从 TenantContextHolder 取，绝不接受前端传参。MyBatis 侧 TenantDatabaseInterceptor
 * 会再自动追加一次 tenant_id（LongValue 字面量），形成双保险；两路都不允许省略。
 *
 * 【一期方案 c：精确扫描】不依赖 pgvector >= 0.8.0 的 hnsw.iterative_scan，
 * 先用 (tenant_id, datasource_id) B-tree 缩小候选，再逐条算余弦距离，召回 100%。
 * 平台总量上来、实测延迟超阈值后再切方案 a（事务内 SET LOCAL hnsw.iterative_scan）。
 *
 * 【按类型分区截断】表级（object_type=1）与列级（object_type=2）<b>各自</b>取 Top-N，
 * 不共用同一个 LIMIT。原因：全库列级 Document 数量是表级的 10~100 倍，且同表列名
 * 语义聚团，共用一个 LIMIT 时一张宽表的列向量可占满全部名额，Top-K 表只聚合出 1 张。
 * 实现用 ROW_NUMBER() OVER (PARTITION BY object_type)，一次扫描完成，成本不增加。
 * scored 显式 MATERIALIZED：避免 CTE 内联后余弦距离在窗口排序与外层投影中各算一遍。
 *
 * 【关键词路】本地 PG 未装 pg_trgm，similarity() 不可用，故用 ILIKE + CASE 加权打分，
 * 不引入任何扩展依赖。关键词在 Service 层清洗（剔除 % _ \ 通配符）后再作为绑定参数传入。
 */
public interface AiSchemaVectorMapper extends BaseMapper<AiSchemaVector> {

    /**
     * 向量路：精确扫描 + tenant_id 硬过滤 + 余弦距离升序。
     *
     * CAST(#{queryVector} AS vector) 用 setString 绑定（JDBC 实测通过）；
     * 不写 embedding <=> #{queryVector} 裸参数，避免 PG 把参数推断成 character varying。
     */
    @Select("""
            WITH scored AS MATERIALIZED (
                SELECT id, object_type, table_id, column_id, schema_name, table_name, column_name,
                       content, struct_version,
                       (embedding <=> CAST(#{queryVector} AS vector)) AS distance
                  FROM ai_schema_vector
                 WHERE tenant_id = #{tenantId}
                   AND datasource_id = #{datasourceId}
                   AND object_type IN (1, 2)
                   AND visible = 1 AND queryable = 1
                   AND sync_status != 4 AND status = 0 AND deleted = 0
                   AND embedding IS NOT NULL
            ),
            ranked AS (
                SELECT scored.*,
                       ROW_NUMBER() OVER (PARTITION BY object_type ORDER BY scored.distance ASC) AS rn
                  FROM scored
            )
            SELECT id, object_type, table_id, column_id, schema_name, table_name, column_name,
                   content, struct_version, distance, NULL AS keyword_score
              FROM ranked
             WHERE rn BETWEEN 1 AND #{perTypeLimit}
             ORDER BY distance ASC
            """)
    @Results(value = {
            @Result(column = "id", property = "id"),
            @Result(column = "object_type", property = "objectType"),
            @Result(column = "table_id", property = "tableId"),
            @Result(column = "column_id", property = "columnId"),
            @Result(column = "schema_name", property = "schemaName"),
            @Result(column = "table_name", property = "tableName"),
            @Result(column = "column_name", property = "columnName"),
            @Result(column = "content", property = "content"),
            @Result(column = "struct_version", property = "structVersion"),
            @Result(column = "distance", property = "distance"),
            @Result(column = "keyword_score", property = "keywordScore")
    })
    List<SchemaVectorHit> searchByVector(@Param("tenantId") Long tenantId,
                                         @Param("datasourceId") Long datasourceId,
                                         @Param("queryVector") String queryVector,
                                         @Param("perTypeLimit") int perTypeLimit);

    /**
     * 关键词路：混合重排的一路信号，也是 Embedding 不可用时的兜底召回。
     *
     * 每个关键词分别命中 table_name / column_name / content 累加打分；
     * 用 != 而非 <>，避免 <script> 里 < 被当作 XML 标签起始符。
     * 关键词由 Service 清洗后传入，列表至少 1 个元素（否则 AND () 语法错误）。
     */
    @Select("""
            <script>
            WITH scored AS MATERIALIZED (
                SELECT id, object_type, table_id, column_id, schema_name, table_name, column_name,
                       content, struct_version,
                       (<foreach collection='keywords' item='kw' separator=' + '>
                           (CASE WHEN table_name ILIKE '%' || #{kw} || '%' THEN 2.0 ELSE 0.0 END
                          + CASE WHEN column_name ILIKE '%' || #{kw} || '%' THEN 1.5 ELSE 0.0 END
                          + CASE WHEN content ILIKE '%' || #{kw} || '%' THEN 1.0 ELSE 0.0 END)
                        </foreach>) AS keyword_score
                  FROM ai_schema_vector
                 WHERE tenant_id = #{tenantId}
                   AND datasource_id = #{datasourceId}
                   AND object_type IN (1, 2)
                   AND visible = 1 AND queryable = 1
                   AND sync_status != 4 AND status = 0 AND deleted = 0
                   AND (<foreach collection='keywords' item='kw' separator=' OR '>
                           (table_name ILIKE '%' || #{kw} || '%'
                            OR column_name ILIKE '%' || #{kw} || '%'
                            OR content ILIKE '%' || #{kw} || '%')
                        </foreach>)
            ),
            ranked AS (
                SELECT scored.*,
                       ROW_NUMBER() OVER (PARTITION BY object_type
                                          ORDER BY scored.keyword_score DESC, scored.id ASC) AS rn
                  FROM scored
            )
            SELECT id, object_type, table_id, column_id, schema_name, table_name, column_name,
                   content, struct_version, NULL AS distance, keyword_score
              FROM ranked
             WHERE rn BETWEEN 1 AND #{perTypeLimit}
             ORDER BY keyword_score DESC, id ASC
            </script>
            """)
    @Results(value = {
            @Result(column = "id", property = "id"),
            @Result(column = "object_type", property = "objectType"),
            @Result(column = "table_id", property = "tableId"),
            @Result(column = "column_id", property = "columnId"),
            @Result(column = "schema_name", property = "schemaName"),
            @Result(column = "table_name", property = "tableName"),
            @Result(column = "column_name", property = "columnName"),
            @Result(column = "content", property = "content"),
            @Result(column = "struct_version", property = "structVersion"),
            @Result(column = "distance", property = "distance"),
            @Result(column = "keyword_score", property = "keywordScore")
    })
    List<SchemaVectorHit> searchByKeyword(@Param("tenantId") Long tenantId,
                                          @Param("datasourceId") Long datasourceId,
                                          @Param("keywords") List<String> keywords,
                                          @Param("perTypeLimit") int perTypeLimit);

}
