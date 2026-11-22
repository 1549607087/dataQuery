package cn.iocoder.boot.module.data_query.framework.recall;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * S3 Schema 召回结果，同时是 S6 的表列白名单与 S4 的变量来源。
 *
 * 该对象既作为 HTTP 响应，也作为 P5 编排器的领域输入，因此刻意不回显
 * tenant_id、deleted、embedding 向量字面量与任何业务数据行。
 *
 * 安全边界：tables / columns 全部来自「向量表过滤 + MyBatis 租户拦截器 +
 * PG 元数据二次核验」三重校验后的可见可查元数据；调用方不得绕过本对象自行拼 Schema。
 */
@Data
@Schema(description = "S3 Schema 召回结果（DDL 片段 + Join + 枚举 + 指标口径 + 命中记录 + S6 白名单）")
public class SchemaContext {

    @Schema(description = "数据源编号")
    private Long datasourceId;

    @Schema(description = "归一化后的问题（S3 的 Embedding 输入）")
    private String question;

    @Schema(description = "召回方式：HYBRID 混合 / VECTOR 仅向量 / KEYWORD_DEGRADED 关键词兜底 / EMPTY 召回为空")
    private String recallMode;

    @Schema(description = "是否降级（Embedding 不可用、限流熔断、pgvector 异常）")
    private Boolean degraded;

    @Schema(description = "降级原因（用户可见，不静默）")
    private String degradeReason;

    @Schema(description = "召回为空或置信度过低，建议走 CLARIFY / RECOMMEND")
    private Boolean recommendClarify;

    @Schema(description = "本租户 Embedding 模型代号")
    private String embeddingModelCode;

    @Schema(description = "向量维度")
    private Integer dimension;

    @Schema(description = "数据源结构版本号，用于事后定位 SQL 依据的结构")
    private Integer structVersion;

    @Schema(description = "SQL 方言")
    private String dialect;

    @Schema(description = "标识符引号 / 是否全引号 / SQL 规则（供 S4 注入）")
    private String identifierQuote;
    private Integer quoteAll;
    private String sqlRules;

    @Schema(description = "拼装后的 Schema 文本（DDL 片段），供 {{schema}} 变量注入")
    private String schemaText;

    @Schema(description = "Token 粗估 / 是否因预算被裁剪")
    private Integer tokenEstimate;
    private Boolean truncated;

    @Schema(description = "召回耗时（毫秒）/ 两路候选数 / 命中表数")
    private Long latencyMs;
    private Integer vectorCandidateCount;
    private Integer keywordCandidateCount;
    private Integer hitTableCount;

    @Schema(description = "Top-K 表及列（S6 白名单主体）")
    private List<TableItem> tables = new ArrayList<>();

    @Schema(description = "已验证且左右表都在候选集内的 Join 路径")
    private List<JoinItem> joins = new ArrayList<>();

    @Schema(description = "一跳关联扩展带进来的表编号（外键维表，可观测用，不影响安全边界）")
    private List<Long> relationExpandedTableIds = new ArrayList<>();

    @Schema(description = "命中记录：相似度、关键词分、RRF 分、来源、秩次")
    private List<HitItem> hits = new ArrayList<>();

    @Schema(description = "S6 表列白名单：只允许模型引用这些 id 对应的表与列")
    private List<Long> tableIds = new ArrayList<>();
    private List<Long> columnIds = new ArrayList<>();

    @Schema(description = "汇总的指标口径与同义词，供 S4 metric_def / synonyms 变量")
    private List<String> metricDefs = new ArrayList<>();
    private List<String> synonyms = new ArrayList<>();

    /** 召回为空 / 降级时构造的空上下文 */
    public static SchemaContext empty(Long datasourceId, String question, String mode,
                                      boolean degraded, String reason) {
        SchemaContext ctx = new SchemaContext();
        ctx.setDatasourceId(datasourceId);
        ctx.setQuestion(question);
        ctx.setRecallMode(mode);
        ctx.setDegraded(degraded);
        ctx.setDegradeReason(reason);
        ctx.setRecommendClarify(true);
        ctx.setSchemaText("");
        ctx.setTokenEstimate(0);
        ctx.setTruncated(false);
        ctx.setLatencyMs(0L);
        ctx.setVectorCandidateCount(0);
        ctx.setKeywordCandidateCount(0);
        ctx.setHitTableCount(0);
        return ctx;
    }

    @Data
    @Schema(description = "召回的表")
    public static class TableItem {
        private Long tableId;
        private String schemaName;
        private String tableName;
        private String nameZh;
        private String description;
        private Integer tableRole;
        private String defaultTimeColumn;
        private Double score;
        private List<ColumnItem> columns = new ArrayList<>();
    }

    @Data
    @Schema(description = "召回的列")
    public static class ColumnItem {
        private Long columnId;
        private String columnName;
        private String nameZh;
        private String dataType;
        private String unit;
        private String metricDef;
        private String synonyms;
        private Integer sensitiveType;
        private String maskRule;
        private Integer ordinalPosition;
        private Integer sort;
        private Double score;
        private List<EnumItem> enums = new ArrayList<>();
    }

    @Data
    @Schema(description = "枚举映射 source_value -> display_name")
    public static class EnumItem {
        private String sourceValue;
        private String displayName;
    }

    @Data
    @Schema(description = "已验证的 Join 路径")
    public static class JoinItem {
        private Long leftTableId;
        private Long rightTableId;
        private String leftTableName;
        private String rightTableName;
        private Integer joinType;
        private String joinPairs;
        private String joinCondition;
        private Integer cost;
        private Integer verified;
    }

    @Data
    @Schema(description = "命中记录")
    public static class HitItem {
        private Long vectorId;
        private Integer objectType;
        private Long tableId;
        private Long columnId;
        private String tableName;
        private String columnName;
        private Double distance;
        private Double keywordScore;
        private Double rrfScore;
        private String source;
        private Integer vectorRank;
        private Integer keywordRank;
    }

}
