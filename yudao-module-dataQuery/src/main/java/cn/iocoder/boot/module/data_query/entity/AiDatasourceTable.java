package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源表元数据（ai_datasource_table）
 *
 * 数据源侧 6 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_datasource_table")
@Data
public class AiDatasourceTable {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_datasource.id
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 所属 schema，冗余存储以支持同一数据源多 schema
     */
    @TableField(value = "schema_name")
    private String schemaName;

    /**
     * 物理表名
     */
    @TableField(value = "table_name")
    private String tableName;

    /**
     * 对象类型：1 表、2 视图、3 物化视图、4 外部表
     */
    @TableField(value = "table_type")
    private Integer tableType;

    /**
     * 业务中文名，喂给 LLM 的 Schema 描述
     */
    @TableField(value = "name_zh")
    private String nameZh;

    /**
     * 业务描述与统计注意事项，Schema 召回时注入提示词
     */
    @TableField(value = "description")
    private String description;

    /**
     * 表角色：1 事实表、2 维表、3 桥表、4 字典/配置表、5 其它
     */
    @TableField(value = "table_role")
    private Integer tableRole;

    /**
     * 主键列名，JSON 数组
     */
    @TableField(value = "primary_key")
    private String primaryKey;

    /**
     * 默认时间字段，用于「本月 / 近 7 天」等时间口径解析
     */
    @TableField(value = "default_time_column")
    private String defaultTimeColumn;

    /**
     * 行数估算，用于 Explain 成本预估与 SQL 生成提示
     */
    @TableField(value = "row_count_estimate")
    private Long rowCountEstimate;

    /**
     * 字段数，元数据同步写入
     */
    @TableField(value = "column_count")
    private Integer columnCount;

    /**
     * 数据预览采样结果快照 JSON（已脱敏，最多 N 行）
     */
    @TableField(value = "sample_rows")
    private String sampleRows;

    /**
     * 最近一次采样时间
     */
    @TableField(value = "sample_time")
    private LocalDateTime sampleTime;

    /**
     * 管理端是否可见：0 否、1 是
     */
    @TableField(value = "visible")
    private Integer visible;

    /**
     * 是否允许 LLM 生成 SQL 时引用：0 否、1 是；静态校验的表白名单
     */
    @TableField(value = "queryable")
    private Integer queryable;

    /**
     * 整表敏感级别：0 无、1 内部、2 敏感、3 高敏
     */
    @TableField(value = "sensitive_level")
    private Integer sensitiveLevel;

    /**
     * 同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除（失效）
     */
    @TableField(value = "sync_status")
    private Integer syncStatus;

    /**
     * 结构指纹（列名+类型+约束的哈希），用于结构变更检测
     */
    @TableField(value = "struct_hash")
    private String structHash;

    /**
     * 最近一次该表的同步时间
     */
    @TableField(value = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * Schema 召回时的排序权重
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
