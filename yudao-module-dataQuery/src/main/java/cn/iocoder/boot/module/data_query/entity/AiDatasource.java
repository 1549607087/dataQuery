package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import cn.iocoder.boot.module.data_query.framework.crypto.AiSecretTypeHandler;

/**
 * 数据源（ai_datasource）
 *
 * 数据源侧 6 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_datasource", autoResultMap = true)
@Data
public class AiDatasource {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 数据源显示名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 数据源编码，租户内唯一，供主题与程序引用
     */
    @TableField(value = "code")
    private String code;

    /**
     * 数据库类型：1 MySQL、2 PostgreSQL、3 Oracle、4 SQLServer、5 达梦、6 人大金仓、7 openGauss、8 ClickHouse、9 Hive
     */
    @TableField(value = "ds_type")
    private Integer dsType;

    /**
     * SQL 方言：postgresql / mysql / oracle / sqlserver / dm / clickhouse；供 ai_prompt_variable.source_key=datasource.dialect 注入
     */
    @TableField(value = "dialect")
    private String dialect;

    /**
     * JDBC 驱动类名
     */
    @TableField(value = "driver_class")
    private String driverClass;

    /**
     * 管理连接 URL，仅用于元数据同步、连通性检测与数据预览
     */
    @TableField(value = "jdbc_url")
    private String jdbcUrl;

    /**
     * 管理连接账号
     */
    @TableField(value = "username")
    private String username;

    /**
     * 管理连接密码（加密存储，接口不回显）
     */
    @TableField(value = "password", typeHandler = AiSecretTypeHandler.class)
    private String password;

    /**
     * 只读查询连接 URL，为空表示复用 jdbc_url（不推荐）
     */
    @TableField(value = "readonly_url")
    private String readonlyUrl;

    /**
     * 只读连接账号，应为仅授予 SELECT 权限的数据库用户
     */
    @TableField(value = "readonly_username")
    private String readonlyUsername;

    /**
     * 只读连接密码（加密存储，接口不回显）
     */
    @TableField(value = "readonly_password", typeHandler = AiSecretTypeHandler.class)
    private String readonlyPassword;

    /**
     * 是否强制用户 SQL 走只读连接：0 否、1 是；生产环境必须为 1
     */
    @TableField(value = "readonly_enabled")
    private Integer readonlyEnabled;

    /**
     * 默认 schema / database，元数据同步的起始范围
     */
    @TableField(value = "schema_name")
    private String schemaName;

    /**
     * 默认 catalog，部分数据库（MySQL / SQLServer）需要
     */
    @TableField(value = "catalog_name")
    private String catalogName;

    /**
     * 标识符引号：PG 双引号、MySQL 反引号、SQLServer 方括号
     */
    @TableField(value = "identifier_quote")
    private String identifierQuote;

    /**
     * 生成 SQL 时是否为所有标识符加引号：0 否、1 是
     */
    @TableField(value = "quote_all")
    private Integer quoteAll;

    /**
     * SQL 安全规则 JSON：deny_keywords / allowed_functions / deny_functions / require_limit / max_joins
     */
    @TableField(value = "sql_rules")
    private String sqlRules;

    /**
     * 单次查询最大返回行数，超限强制 LIMIT 或截断
     */
    @TableField(value = "max_rows")
    private Integer maxRows;

    /**
     * 单次查询最大扫描行数，0 表示不限（依赖 Explain 预估）
     */
    @TableField(value = "max_scan_rows")
    private Long maxScanRows;

    /**
     * 查询超时（毫秒），超时由 JDBC cancel 中止
     */
    @TableField(value = "query_timeout_ms")
    private Integer queryTimeoutMs;

    /**
     * 只读连接池最小空闲连接数
     */
    @TableField(value = "pool_min_idle")
    private Integer poolMinIdle;

    /**
     * 只读连接池最大活跃连接数
     */
    @TableField(value = "pool_max_active")
    private Integer poolMaxActive;

    /**
     * 连接有效性校验 SQL，默认 SELECT 1，禁止 SELECT 1 FROM DUAL
     */
    @TableField(value = "pool_validation_sql")
    private String poolValidationSql;

    /**
     * 是否允许该数据源执行 DDL：0 否、1 是；元数据同步只读，恒为 0
     */
    @TableField(value = "allow_ddl")
    private Integer allowDdl;

    /**
     * 允许同步的 schema 白名单，JSON 数组，空表示全部
     */
    @TableField(value = "allowed_schemas")
    private String allowedSchemas;

    /**
     * 表过滤规则 JSON：include / exclude 正则数组，用于排除系统表与临时表
     */
    @TableField(value = "table_filter")
    private String tableFilter;

    /**
     * 定时元数据同步 Quartz cron，空表示不自动同步
     */
    @TableField(value = "sync_cron")
    private String syncCron;

    /**
     * 同步状态：0 未同步、1 同步中、2 成功、3 失败
     */
    @TableField(value = "sync_status")
    private Integer syncStatus;

    /**
     * 最近一次元数据同步完成时间
     */
    @TableField(value = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 结构版本号，每次检测到表结构变更自增
     */
    @TableField(value = "struct_version")
    private Integer structVersion;

    /**
     * 健康状态：0 未检测、1 健康、2 异常
     */
    @TableField(value = "health_status")
    private Integer healthStatus;

    /**
     * 最近一次连通性检测时间
     */
    @TableField(value = "last_check_time")
    private LocalDateTime lastCheckTime;

    /**
     * 最近一次连通性检测结果或错误摘要
     */
    @TableField(value = "last_check_result")
    private String lastCheckResult;

    /**
     * 状态：0 启用、1 停用、2 维护中
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 排序
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

    /**
     * 租户编号；数据源连接配置租户私有，0 仅为模板/演示数据，不参与业务读取
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
