package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源字段元数据（ai_datasource_column）
 *
 * 数据源侧 6 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_datasource_column")
@Data
public class AiDatasourceColumn {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_datasource.id，冗余便于按数据源检索
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 关联 ai_datasource_table.id
     */
    @TableField(value = "table_id")
    private Long tableId;

    /**
     * 所属 schema，冗余
     */
    @TableField(value = "schema_name")
    private String schemaName;

    /**
     * 物理表名，冗余便于拼接 SQL
     */
    @TableField(value = "table_name")
    private String tableName;

    /**
     * 物理列名
     */
    @TableField(value = "column_name")
    private String columnName;

    /**
     * 字段业务中文名，口径解释与列裁剪用
     */
    @TableField(value = "name_zh")
    private String nameZh;

    /**
     * 字段业务描述，喂给 LLM
     */
    @TableField(value = "description")
    private String description;

    /**
     * 数据库原始类型，如 numeric(12,2) / varchar(64)
     */
    @TableField(value = "data_type")
    private String dataType;

    /**
     * JDBC 类型名，用于结果集解析
     */
    @TableField(value = "jdbc_type")
    private String jdbcType;

    /**
     * 长度 / 精度
     */
    @TableField(value = "column_size")
    private Integer columnSize;

    /**
     * 是否允许为空：0 否、1 是
     */
    @TableField(value = "nullable")
    private Integer nullable;

    /**
     * 是否主键列：0 否、1 是
     */
    @TableField(value = "primary_key")
    private Integer primaryKey;

    /**
     * 是否自增：0 否、1 是
     */
    @TableField(value = "auto_increment")
    private Integer autoIncrement;

    /**
     * 数据库默认值
     */
    @TableField(value = "default_value")
    private String defaultValue;

    /**
     * 字段在表中的物理顺序
     */
    @TableField(value = "ordinal_position")
    private Integer ordinalPosition;

    /**
     * 语义角色：1 维度、2 指标、3 时间、4 主键、5 外键、6 标识/无关、7 敏感
     */
    @TableField(value = "semantic_role")
    private Integer semanticRole;

    /**
     * 默认聚合方式：SUM / COUNT / AVG / MAX / MIN / NONE
     */
    @TableField(value = "aggregation")
    private String aggregation;

    /**
     * 业务单位：元 / 人 / 次 / % / 件
     */
    @TableField(value = "unit")
    private String unit;

    /**
     * 时间格式：yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / unix_ms
     */
    @TableField(value = "time_format")
    private String timeFormat;

    /**
     * 是否为该表默认时间字段：0 否、1 是
     */
    @TableField(value = "is_default_time")
    private Integer isDefaultTime;

    /**
     * 指标口径定义（计算逻辑 + 过滤条件），供 ai_prompt_variable.source_key=metadata.metric_def 注入口径解释
     */
    @TableField(value = "metric_def")
    private String metricDef;

    /**
     * 业务术语与同义词 JSON 数组，供问题改写与召回归一
     */
    @TableField(value = "synonyms")
    private String synonyms;

    /**
     * 样例值 JSON 数组（已脱敏），供 LLM 判断枚举与取值范围
     */
    @TableField(value = "sample_values")
    private String sampleValues;

    /**
     * 去重值数量估算，用于判断是否枚举列
     */
    @TableField(value = "distinct_count_estimate")
    private Long distinctCountEstimate;

    /**
     * 是否维护枚举映射：0 否、1 是，指向 ai_datasource_column_enum
     */
    @TableField(value = "enum_enabled")
    private Integer enumEnabled;

    /**
     * 敏感类型：0 无、1 手机号、2 身份证、3 姓名、4 邮箱、5 银行卡、6 金额、9 自定义
     */
    @TableField(value = "sensitive_type")
    private Integer sensitiveType;

    /**
     * 脱敏规则：phone / idcard / partial / hash / forbid；forbid 表示该列禁止出现在结果中
     */
    @TableField(value = "mask_rule")
    private String maskRule;

    /**
     * 管理端是否可见：0 否、1 是
     */
    @TableField(value = "visible")
    private Integer visible;

    /**
     * 是否允许被 LLM 选中：0 否、1 是；静态校验的列白名单，越权列的最后一道防线
     */
    @TableField(value = "queryable")
    private Integer queryable;

    /**
     * 是否允许作为 WHERE 条件：0 否、1 是
     */
    @TableField(value = "filterable")
    private Integer filterable;

    /**
     * 同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除
     */
    @TableField(value = "sync_status")
    private Integer syncStatus;

    /**
     * 字段结构指纹，用于结构变更检测
     */
    @TableField(value = "struct_hash")
    private String structHash;

    /**
     * Schema 召回时的列裁剪优先级
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

    /**
     * 租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * creator
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * create_time
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * updater
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * update_time
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * deleted
     */
    @TableField(value = "deleted")
    private Integer deleted;

}
