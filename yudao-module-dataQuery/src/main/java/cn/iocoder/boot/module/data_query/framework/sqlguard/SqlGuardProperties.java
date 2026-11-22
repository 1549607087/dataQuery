package cn.iocoder.boot.module.data_query.framework.sqlguard;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * P4 S6/S7 可调参数。
 *
 * 安全红线不可通过配置关闭：语句类型白名单、单语句、内置禁用关键字/函数基线、
 * 服务端元数据二次核验、白名单取交集、强制 LIMIT、只读连接，全部硬编码在校验器里。
 * 这里只调「复杂度上限、行数上限、超时上限、脱敏占位符」，以及允许运维**追加**
 * 禁用关键字 / 函数（extra-*，只能加不能删）。
 */
@Component
@ConfigurationProperties(prefix = "data-query.sql-guard")
@Data
public class SqlGuardProperties {

    /** 抽取后 SQL 文本长度上限（字符） */
    private Integer maxSqlLength = 8000;

    /** 子查询 / CTE 个数上限 */
    private Integer maxSubqueries = 3;

    /** 单条 SQL 允许引用的物理表数量上限 */
    private Integer maxTables = 8;

    /** 单条 SQL 允许引用的列数量上限（去重后） */
    private Integer maxColumns = 80;

    /** JOIN 数量上限基准；与 ai_datasource.sql_rules.max_joins 取较小值 */
    private Integer maxJoins = 5;

    /** 问数侧默认返回行数上限；与 ai_datasource.max_rows 取较小值 */
    private Integer defaultMaxRows = 1000;

    /** 问数侧查询超时上限（毫秒）；与 ai_datasource.query_timeout_ms 取较小值 */
    private Integer maxQueryTimeoutMs = 10000;

    /** JDBC fetchSize */
    private Integer fetchSize = 500;

    /** 脱敏占位符 */
    private String maskPlaceholder = "******";

    /** sensitive_type > 0 但没配 mask_rule 的列是否默认脱敏 */
    private Boolean maskSensitiveByDefault = true;

    /** 运维追加的禁用函数（只能追加，内置基线不可删） */
    private List<String> extraDenyFunctions = new ArrayList<>();

    /** 运维追加的禁用关键字（只能追加，内置基线不可删） */
    private List<String> extraDenyKeywords = new ArrayList<>();

}
