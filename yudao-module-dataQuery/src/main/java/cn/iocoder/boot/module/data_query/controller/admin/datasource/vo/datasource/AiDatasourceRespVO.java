package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 数据源 Response VO
 */
@Schema(description = "管理后台 - 数据源 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiDatasourceRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "数据源显示名称")
    @ExcelProperty("数据源显示名称")
    private String name;

    @Schema(description = "数据源编码，租户内唯一，供主题与程序引用")
    @ExcelProperty("数据源编码")
    private String code;

    @Schema(description = "数据库类型：1 MySQL、2 PostgreSQL、3 Oracle、4 SQLServer、5 达梦、6 人大金仓、7 openGauss、8 ClickHouse、9 Hive")
    @ExcelProperty("数据库类型")
    private Integer dsType;

    @Schema(description = "SQL 方言：postgresql / mysql / oracle / sqlserver / dm / clickhouse；供 ai_prompt_variable.source_key=datasource.dialect 注入")
    @ExcelProperty("SQL 方言")
    private String dialect;

    @Schema(description = "JDBC 驱动类名")
    @ExcelProperty("JDBC 驱动类名")
    private String driverClass;

    @Schema(description = "管理连接 URL，仅用于元数据同步、连通性检测与数据预览")
    @ExcelProperty("管理连接 URL")
    private String jdbcUrl;

    @Schema(description = "管理连接账号")
    @ExcelProperty("管理连接账号")
    private String username;

    @Schema(description = "只读查询连接 URL，为空表示复用 jdbc_url（不推荐）")
    @ExcelProperty("只读查询连接 URL")
    private String readonlyUrl;

    @Schema(description = "只读连接账号，应为仅授予 SELECT 权限的数据库用户")
    @ExcelProperty("只读连接账号")
    private String readonlyUsername;

    @Schema(description = "是否强制用户 SQL 走只读连接：0 否、1 是；生产环境必须为 1")
    @ExcelProperty("是否强制用户 SQL 走只读")
    private Integer readonlyEnabled;

    @Schema(description = "默认 schema / database，元数据同步的起始范围")
    @ExcelProperty("默认 schema / da")
    private String schemaName;

    @Schema(description = "默认 catalog，部分数据库（MySQL / SQLServer）需要")
    @ExcelProperty("默认 catalog")
    private String catalogName;

    @Schema(description = "标识符引号：PG 双引号、MySQL 反引号、SQLServer 方括号")
    @ExcelProperty("标识符引号")
    private String identifierQuote;

    @Schema(description = "生成 SQL 时是否为所有标识符加引号：0 否、1 是")
    @ExcelProperty("生成 SQL 时是否为所有标")
    private Integer quoteAll;

    @Schema(description = "SQL 安全规则 JSON：deny_keywords / allowed_functions / deny_functions / require_limit / max_joins")
    @ExcelProperty("SQL 安全规则 JSON")
    private String sqlRules;

    @Schema(description = "单次查询最大返回行数，超限强制 LIMIT 或截断")
    @ExcelProperty("单次查询最大返回行数")
    private Integer maxRows;

    @Schema(description = "单次查询最大扫描行数，0 表示不限（依赖 Explain 预估）")
    @ExcelProperty("单次查询最大扫描行数")
    private Long maxScanRows;

    @Schema(description = "查询超时（毫秒），超时由 JDBC cancel 中止")
    @ExcelProperty("查询超时（毫秒）")
    private Integer queryTimeoutMs;

    @Schema(description = "只读连接池最小空闲连接数")
    @ExcelProperty("只读连接池最小空闲连接数")
    private Integer poolMinIdle;

    @Schema(description = "只读连接池最大活跃连接数")
    @ExcelProperty("只读连接池最大活跃连接数")
    private Integer poolMaxActive;

    @Schema(description = "连接有效性校验 SQL，默认 SELECT 1，禁止 SELECT 1 FROM DUAL")
    @ExcelProperty("连接有效性校验 SQL")
    private String poolValidationSql;

    @Schema(description = "是否允许该数据源执行 DDL：0 否、1 是；元数据同步只读，恒为 0")
    @ExcelProperty("是否允许该数据源执行 DDL")
    private Integer allowDdl;

    @Schema(description = "允许同步的 schema 白名单，JSON 数组，空表示全部")
    @ExcelProperty("允许同步的 schema 白")
    private String allowedSchemas;

    @Schema(description = "表过滤规则 JSON：include / exclude 正则数组，用于排除系统表与临时表")
    @ExcelProperty("表过滤规则 JSON")
    private String tableFilter;

    @Schema(description = "定时元数据同步 Quartz cron，空表示不自动同步")
    @ExcelProperty("定时元数据同步 Quartz")
    private String syncCron;

    @Schema(description = "同步状态：0 未同步、1 同步中、2 成功、3 失败")
    @ExcelProperty("同步状态")
    private Integer syncStatus;

    @Schema(description = "最近一次元数据同步完成时间")
    @ExcelProperty("最近一次元数据同步完成时间")
    private LocalDateTime lastSyncTime;

    @Schema(description = "结构版本号，每次检测到表结构变更自增")
    @ExcelProperty("结构版本号")
    private Integer structVersion;

    @Schema(description = "健康状态：0 未检测、1 健康、2 异常")
    @ExcelProperty("健康状态")
    private Integer healthStatus;

    @Schema(description = "最近一次连通性检测时间")
    @ExcelProperty("最近一次连通性检测时间")
    private LocalDateTime lastCheckTime;

    @Schema(description = "最近一次连通性检测结果或错误摘要")
    @ExcelProperty("最近一次连通性检测结果或错误")
    private String lastCheckResult;

    @Schema(description = "状态：0 启用、1 停用、2 维护中")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @Schema(description = "管理连接密码是否已配置")
    @ExcelProperty("管理密码已配置")
    private Boolean passwordConfigured;

    @Schema(description = "只读连接密码是否已配置")
    @ExcelProperty("只读密码已配置")
    private Boolean readonlyPasswordConfigured;

}
