package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 数据源新增/修改 Request VO
 *
 * 同步 / 检测 / 健康状态等运行时字段由服务端回写，不接受前端提交；
 * tenant_id 一律由服务端按当前租户上下文写入，禁止前端传入。
 */
@Schema(description = "管理后台 - 数据源新增/修改 Request VO")
@Data
public class AiDatasourceSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "数据源显示名称", example = "")
    @NotEmpty(message = "Name不能为空")
    private String name;

    @Schema(description = "数据源编码，租户内唯一，供主题与程序引用", example = "")
    @NotEmpty(message = "Code不能为空")
    private String code;

    @Schema(description = "数据库类型：1 MySQL、2 PostgreSQL、3 Oracle、4 SQLServer、5 达梦、6 人大金仓、7 openGauss、8 ClickHouse、9 Hive", example = "")
    @NotNull(message = "DsType不能为空")
    private Integer dsType;

    @Schema(description = "SQL 方言：postgresql / mysql / oracle / sqlserver / dm / clickhouse；供 ai_prompt_variable.source_key=datasource.dialect 注入", example = "")
    private String dialect;

    @Schema(description = "JDBC 驱动类名", example = "")
    private String driverClass;

    @Schema(description = "管理连接 URL，仅用于元数据同步、连通性检测与数据预览", example = "")
    private String jdbcUrl;

    @Schema(description = "管理连接账号", example = "")
    private String username;

    @Schema(description = "管理连接密码（留空表示不修改，加密存储，接口不回显）", example = "")
    private String password;

    @Schema(description = "只读查询连接 URL，为空表示复用 jdbc_url（不推荐）", example = "")
    private String readonlyUrl;

    @Schema(description = "只读连接账号，应为仅授予 SELECT 权限的数据库用户", example = "")
    private String readonlyUsername;

    @Schema(description = "只读连接密码（留空表示不修改，加密存储，接口不回显）", example = "")
    private String readonlyPassword;

    @Schema(description = "是否强制用户 SQL 走只读连接：0 否、1 是；生产环境必须为 1", example = "")
    private Integer readonlyEnabled;

    @Schema(description = "默认 schema / database，元数据同步的起始范围", example = "")
    private String schemaName;

    @Schema(description = "默认 catalog，部分数据库（MySQL / SQLServer）需要", example = "")
    private String catalogName;

    @Schema(description = "标识符引号：PG 双引号、MySQL 反引号、SQLServer 方括号", example = "")
    private String identifierQuote;

    @Schema(description = "生成 SQL 时是否为所有标识符加引号：0 否、1 是", example = "")
    private Integer quoteAll;

    @Schema(description = "SQL 安全规则 JSON：deny_keywords / allowed_functions / deny_functions / require_limit / max_joins", example = "")
    private String sqlRules;

    @Schema(description = "单次查询最大返回行数，超限强制 LIMIT 或截断", example = "")
    private Integer maxRows;

    @Schema(description = "单次查询最大扫描行数，0 表示不限（依赖 Explain 预估）", example = "")
    private Long maxScanRows;

    @Schema(description = "查询超时（毫秒），超时由 JDBC cancel 中止", example = "")
    private Integer queryTimeoutMs;

    @Schema(description = "只读连接池最小空闲连接数", example = "")
    private Integer poolMinIdle;

    @Schema(description = "只读连接池最大活跃连接数", example = "")
    private Integer poolMaxActive;

    @Schema(description = "连接有效性校验 SQL，默认 SELECT 1，禁止 SELECT 1 FROM DUAL", example = "")
    private String poolValidationSql;

    @Schema(description = "是否允许该数据源执行 DDL：0 否、1 是；元数据同步只读，恒为 0", example = "")
    private Integer allowDdl;

    @Schema(description = "允许同步的 schema 白名单，JSON 数组，空表示全部", example = "")
    private String allowedSchemas;

    @Schema(description = "表过滤规则 JSON：include / exclude 正则数组，用于排除系统表与临时表", example = "")
    private String tableFilter;

    @Schema(description = "定时元数据同步 Quartz cron，空表示不自动同步", example = "")
    private String syncCron;

    @Schema(description = "状态：0 启用、1 停用、2 维护中", example = "")
    @NotNull(message = "Status不能为空")
    private Integer status;

    @Schema(description = "排序", example = "")
    private Integer sort;

    @Schema(description = "备注", example = "")
    private String remark;

}
