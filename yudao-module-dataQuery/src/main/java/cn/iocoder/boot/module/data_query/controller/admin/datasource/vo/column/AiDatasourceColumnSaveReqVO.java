package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.column;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 数据源字段元数据新增/修改 Request VO
 *
 * 同步 / 检测 / 健康状态等运行时字段由服务端回写，不接受前端提交；
 * tenant_id 一律由服务端按当前租户上下文写入，禁止前端传入。
 */
@Schema(description = "管理后台 - 数据源字段元数据新增/修改 Request VO")
@Data
public class AiDatasourceColumnSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "关联 ai_datasource.id，冗余便于按数据源检索", example = "")
    @NotNull(message = "DatasourceId不能为空")
    private Long datasourceId;

    @Schema(description = "关联 ai_datasource_table.id", example = "")
    @NotNull(message = "TableId不能为空")
    private Long tableId;

    @Schema(description = "所属 schema，冗余", example = "")
    private String schemaName;

    @Schema(description = "物理表名，冗余便于拼接 SQL", example = "")
    private String tableName;

    @Schema(description = "物理列名", example = "")
    @NotEmpty(message = "ColumnName不能为空")
    private String columnName;

    @Schema(description = "字段业务中文名，口径解释与列裁剪用", example = "")
    private String nameZh;

    @Schema(description = "字段业务描述，喂给 LLM", example = "")
    private String description;

    @Schema(description = "数据库原始类型，如 numeric(12,2) / varchar(64)", example = "")
    private String dataType;

    @Schema(description = "JDBC 类型名，用于结果集解析", example = "")
    private String jdbcType;

    @Schema(description = "长度 / 精度", example = "")
    private Integer columnSize;

    @Schema(description = "是否允许为空：0 否、1 是", example = "")
    private Integer nullable;

    @Schema(description = "是否主键列：0 否、1 是", example = "")
    private Integer primaryKey;

    @Schema(description = "是否自增：0 否、1 是", example = "")
    private Integer autoIncrement;

    @Schema(description = "数据库默认值", example = "")
    private String defaultValue;

    @Schema(description = "字段在表中的物理顺序", example = "")
    private Integer ordinalPosition;

    @Schema(description = "语义角色：1 维度、2 指标、3 时间、4 主键、5 外键、6 标识/无关、7 敏感", example = "")
    private Integer semanticRole;

    @Schema(description = "默认聚合方式：SUM / COUNT / AVG / MAX / MIN / NONE", example = "")
    private String aggregation;

    @Schema(description = "业务单位：元 / 人 / 次 / % / 件", example = "")
    private String unit;

    @Schema(description = "时间格式：yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / unix_ms", example = "")
    private String timeFormat;

    @Schema(description = "是否为该表默认时间字段：0 否、1 是", example = "")
    private Integer isDefaultTime;

    @Schema(description = "指标口径定义（计算逻辑 + 过滤条件），供 ai_prompt_variable.source_key=metadata.metric_def 注入口径解释", example = "")
    private String metricDef;

    @Schema(description = "业务术语与同义词 JSON 数组，供问题改写与召回归一", example = "")
    private String synonyms;

    @Schema(description = "样例值 JSON 数组（已脱敏），供 LLM 判断枚举与取值范围", example = "")
    private String sampleValues;

    @Schema(description = "是否维护枚举映射：0 否、1 是，指向 ai_datasource_column_enum", example = "")
    private Integer enumEnabled;

    @Schema(description = "敏感类型：0 无、1 手机号、2 身份证、3 姓名、4 邮箱、5 银行卡、6 金额、9 自定义", example = "")
    private Integer sensitiveType;

    @Schema(description = "脱敏规则：phone / idcard / partial / hash / forbid；forbid 表示该列禁止出现在结果中", example = "")
    private String maskRule;

    @Schema(description = "管理端是否可见：0 否、1 是", example = "")
    private Integer visible;

    @Schema(description = "是否允许被 LLM 选中：0 否、1 是；静态校验的列白名单，越权列的最后一道防线", example = "")
    private Integer queryable;

    @Schema(description = "是否允许作为 WHERE 条件：0 否、1 是", example = "")
    private Integer filterable;

    @Schema(description = "Schema 召回时的列裁剪优先级", example = "")
    private Integer sort;

    @Schema(description = "备注", example = "")
    private String remark;

}
