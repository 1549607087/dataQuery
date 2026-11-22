package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.column;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 数据源字段元数据 Response VO
 */
@Schema(description = "管理后台 - 数据源字段元数据 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiDatasourceColumnRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_datasource.id，冗余便于按数据源检索")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "关联 ai_datasource_table.id")
    @ExcelProperty("关联 ai_datasour")
    private Long tableId;

    @Schema(description = "所属 schema，冗余")
    @ExcelProperty("所属 schema")
    private String schemaName;

    @Schema(description = "物理表名，冗余便于拼接 SQL")
    @ExcelProperty("物理表名")
    private String tableName;

    @Schema(description = "物理列名")
    @ExcelProperty("物理列名")
    private String columnName;

    @Schema(description = "字段业务中文名，口径解释与列裁剪用")
    @ExcelProperty("字段业务中文名")
    private String nameZh;

    @Schema(description = "字段业务描述，喂给 LLM")
    @ExcelProperty("字段业务描述")
    private String description;

    @Schema(description = "数据库原始类型，如 numeric(12,2) / varchar(64)")
    @ExcelProperty("数据库原始类型")
    private String dataType;

    @Schema(description = "JDBC 类型名，用于结果集解析")
    @ExcelProperty("JDBC 类型名")
    private String jdbcType;

    @Schema(description = "长度 / 精度")
    @ExcelProperty("长度 / 精度")
    private Integer columnSize;

    @Schema(description = "是否允许为空：0 否、1 是")
    @ExcelProperty("是否允许为空")
    private Integer nullable;

    @Schema(description = "是否主键列：0 否、1 是")
    @ExcelProperty("是否主键列")
    private Integer primaryKey;

    @Schema(description = "是否自增：0 否、1 是")
    @ExcelProperty("是否自增")
    private Integer autoIncrement;

    @Schema(description = "数据库默认值")
    @ExcelProperty("数据库默认值")
    private String defaultValue;

    @Schema(description = "字段在表中的物理顺序")
    @ExcelProperty("字段在表中的物理顺序")
    private Integer ordinalPosition;

    @Schema(description = "语义角色：1 维度、2 指标、3 时间、4 主键、5 外键、6 标识/无关、7 敏感")
    @ExcelProperty("语义角色")
    private Integer semanticRole;

    @Schema(description = "默认聚合方式：SUM / COUNT / AVG / MAX / MIN / NONE")
    @ExcelProperty("默认聚合方式")
    private String aggregation;

    @Schema(description = "业务单位：元 / 人 / 次 / % / 件")
    @ExcelProperty("业务单位")
    private String unit;

    @Schema(description = "时间格式：yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / unix_ms")
    @ExcelProperty("时间格式")
    private String timeFormat;

    @Schema(description = "是否为该表默认时间字段：0 否、1 是")
    @ExcelProperty("是否为该表默认时间字段")
    private Integer isDefaultTime;

    @Schema(description = "指标口径定义（计算逻辑 + 过滤条件），供 ai_prompt_variable.source_key=metadata.metric_def 注入口径解释")
    @ExcelProperty("指标口径定义（计算逻辑 + ")
    private String metricDef;

    @Schema(description = "业务术语与同义词 JSON 数组，供问题改写与召回归一")
    @ExcelProperty("业务术语与同义词 JSON ")
    private String synonyms;

    @Schema(description = "样例值 JSON 数组（已脱敏），供 LLM 判断枚举与取值范围")
    @ExcelProperty("样例值 JSON 数组（已脱")
    private String sampleValues;

    @Schema(description = "去重值数量估算，用于判断是否枚举列")
    @ExcelProperty("去重值数量估算")
    private Long distinctCountEstimate;

    @Schema(description = "是否维护枚举映射：0 否、1 是，指向 ai_datasource_column_enum")
    @ExcelProperty("是否维护枚举映射")
    private Integer enumEnabled;

    @Schema(description = "敏感类型：0 无、1 手机号、2 身份证、3 姓名、4 邮箱、5 银行卡、6 金额、9 自定义")
    @ExcelProperty("敏感类型")
    private Integer sensitiveType;

    @Schema(description = "脱敏规则：phone / idcard / partial / hash / forbid；forbid 表示该列禁止出现在结果中")
    @ExcelProperty("脱敏规则")
    private String maskRule;

    @Schema(description = "管理端是否可见：0 否、1 是")
    @ExcelProperty("管理端是否可见")
    private Integer visible;

    @Schema(description = "是否允许被 LLM 选中：0 否、1 是；静态校验的列白名单，越权列的最后一道防线")
    @ExcelProperty("是否允许被 LLM 选中")
    private Integer queryable;

    @Schema(description = "是否允许作为 WHERE 条件：0 否、1 是")
    @ExcelProperty("是否允许作为 WHERE 条")
    private Integer filterable;

    @Schema(description = "同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除")
    @ExcelProperty("同步状态")
    private Integer syncStatus;

    @Schema(description = "字段结构指纹，用于结构变更检测")
    @ExcelProperty("字段结构指纹")
    private String structHash;

    @Schema(description = "Schema 召回时的列裁剪优先级")
    @ExcelProperty("Schema 召回时的列裁剪")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;


}
