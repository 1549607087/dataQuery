package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SchemaWhitelist;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SafeSql;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SqlGuardProperties;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SqlGuardResult;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceColumnMapper;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceTableMapper;
import cn.iocoder.boot.module.data_query.service.SchemaRecallService;
import cn.iocoder.boot.module.data_query.service.SqlGuardService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.AllValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S6 SQL 静态校验器实现（JSQLParser 5.2，AST 级，不依赖正则黑名单兜底）。
 *
 * 已在 WbP4ParserProbe / WbP4Probe2 / WbP4Probe3 / WbP4Probe4 实测的版本边界：
 * 1. CCJSqlParserUtil.parse(sql) 遇到 "SELECT 1; DROP TABLE x" **只返回第一条**，
 *    必须用 parseStatements() 校验语句数，否则多语句注入会被静默放行；
 * 2. "FOR UPDATE / FOR SHARE" 落在 Select#getForMode()，getForClause() 恒为 null；
 * 3. "SELECT ... INTO" 落在 PlainSelect#getIntoTables() / getIntoTempTable()；
 * 4. SetOperationList#setLimit() 会产出 "... LIMIT 10 LIMIT 7" 的非法双 LIMIT，
 *    因此一期只接受 PlainSelect（含 WITH items），UNION / VALUES / TABLE 明确拒绝；
 * 5. count(*) 的 "*" 是 Function 内部的 AllColumns，禁止 SELECT * 只能看
 *    SelectItem#getExpression() 的直接类型，不能靠 visitor 收集到的 AllColumns；
 * 6. LIMIT ALL -> AllValue、LIMIT ? -> JdbcParameter，只接受 LongValue 字面量；
 * 7. TablesNamesFinder#getTables() 会自动排除 CTE 名 / 派生表别名，只留物理表；
 *    Column#getTableName() 对 "x.id" 返回别名 x，故列校验按列名 + SELECT 别名集合，
 *    不按限定名反查表，避免误杀 CTE 输出列。
 *
 * 安全红线（不可配置关闭）：单语句、语句类型白名单、内置禁用关键字/函数基线、
 * 服务端 PG 元数据二次核验、白名单取交集、强制 LIMIT ≤ max_rows、禁止参数占位符。
 */
@Slf4j
@Service
public class SqlGuardServiceImpl implements SqlGuardService {

    /** 内置禁用关键字基线：运维只能通过 extra-deny-keywords 追加，不能删 */
    private static final Set<String> BASE_DENY_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "GRANT", "REVOKE",
            "CREATE", "COPY", "VACUUM", "ANALYZE", "MERGE", "REPLACE", "EXECUTE", "CALL",
            "LOCK", "COMMIT", "ROLLBACK", "SAVEPOINT", "SET", "RESET", "DISCARD",
            "INTO", "OUTFILE", "DUMPFILE");
    // 说明：pg_sleep / dblink / load_file 等纯函数不在关键字基线里重复登记，
    // 由 BASE_DENY_FUNCTIONS 做 AST 级精确匹配，避免同一风险报出两个不同错误码。

    /** 内置禁用函数基线：文件系统 / 进程 / 网络 / 锁 / 配置读取 / 延时 */
    private static final Set<String> BASE_DENY_FUNCTIONS = Set.of(
            "pg_sleep", "pg_sleep_for", "pg_sleep_until", "dblink", "dblink_exec",
            "pg_read_file", "pg_read_binary_file", "pg_ls_dir", "pg_stat_file",
            "pg_logdir_ls", "lo_import", "lo_export", "lo_from_bytea",
            "pg_execute_server_program", "pg_terminate_backend", "pg_cancel_backend",
            "current_setting", "set_config", "pg_advisory_lock", "pg_advisory_xact_lock",
            "query_to_xml", "database_to_xml", "xpath", "load_file", "benchmark", "sleep",
            "sys_exec", "sys_eval", "xp_cmdshell", "utl_http", "dbms_pipe", "pg_notify");

    private static final Pattern CODE_BLOCK = Pattern.compile("(?s)```(?:sql|SQL|Sql)?\\s*(.*?)```");

    private static final Pattern FOR_UPDATE_TEXT = Pattern.compile(
            "(?i)\\bFOR\\s+(UPDATE|SHARE|NO\\s+KEY\\s+UPDATE|KEY\\s+SHARE)\\b|\\bFETCH\\s+(FIRST|NEXT)\\b");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int SYNC_STATUS_RETIRED = 4;

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;
    @Resource
    private AiDatasourceTableMapper tableMapper;
    @Resource
    private AiDatasourceColumnMapper columnMapper;
    @Resource
    private SchemaRecallService schemaRecallService;
    @Resource
    private SqlGuardProperties properties;

    @Override
    public SqlGuardResult validate(Long datasourceId, String rawText, String question,
                                   SchemaContext schemaContext) {
        SqlGuardResult result = new SqlGuardResult();
        result.setPassed(false);
        result.setLimitApplied(false);
        result.setLimitClamped(false);
        String sql = null;
        try {
            // ---- 1. SQL 抽取与文本级上限 ----
            sql = extractSql(rawText, result);
            result.setOriginalSql(sql);
            if (!StringUtils.hasText(sql)) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_SQL_EMPTY, null);
            }
            int maxLength = positive(properties.getMaxSqlLength(), 8000);
            if (sql.length() > maxLength) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_SQL_TOO_LONG,
                        "长度 " + sql.length() + " > " + maxLength);
            }
            result.check("SQL 抽取：长度 " + sql.length() + " 字符，上限 " + maxLength);

            // ---- 2. 数据源归属（Interceptor + 显式 tenant 比对） ----
            AiDatasource ds = ownerValidator.requireOwned(datasourceId);
            result.check("数据源归属校验通过：datasourceId=" + datasourceId);

            // ---- 3. S3 白名单（服务端重载 PG 元数据并取交集） ----
            SchemaWhitelist whitelist = loadWhitelist(ds, question, schemaContext, result);
            result.setWhitelistTables(new ArrayList<>(whitelist.getTableNames()));
            result.setWhitelistColumns(new ArrayList<>(whitelist.getColumnNames()));
            result.check("S6 白名单：表 " + whitelist.getTableNames().size()
                    + " 张 / 列 " + whitelist.getColumnNames().size()
                    + " 个（召回 ID ∩ PG 可见可查元数据）");

            // ---- 4. sql_rules（真实 JSON 只有 deny_keywords / require_limit / max_joins） ----
            RuleSet rules = parseRules(ds, result);

            // ---- 5. 单语句 + 语句类型 ----
            Statements statements;
            try {
                statements = CCJSqlParserUtil.parseStatements(sql);
            } catch (Exception ex) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_PARSE_FAILED, brief(ex));
                return result;
            }
            if (statements.getStatements() == null || statements.getStatements().size() != 1) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_MULTI_STATEMENT,
                        "语句数 " + (statements.getStatements() == null ? 0 : statements.getStatements().size()));
            }
            Statement statement = statements.getStatements().get(0);
            if (!(statement instanceof PlainSelect)) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_UNSUPPORTED_STATEMENT,
                        "实际类型 " + statement.getClass().getSimpleName());
            }
            result.check("单语句校验通过：PlainSelect（含 WITH ... SELECT）");

            Select select = (Select) statement;
            // ---- 6. 顶层禁止子句 ----
            if (hasForbiddenClause(select)) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_FORBIDDEN_CLAUSE,
                        "FOR UPDATE / FOR SHARE / 锁等待 / FETCH 不受支持");
            }
            String scrubbed = scrubLiterals(select.toString());
            if (FOR_UPDATE_TEXT.matcher(scrubbed).find()) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_FORBIDDEN_CLAUSE,
                        "检测到行锁 / FETCH 语法");
            }
            result.check("行锁 / FETCH / 只读破坏性子句：无");

            // ---- 7. AST 全量遍历（含 CTE、子查询、表达式） ----
            Collector collector = new Collector();
            Set<String> tables = collector.getTables(statement);
            if (collector.selectStarFound) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_SELECT_STAR,
                        "必须显式列出字段；count(*) 等聚合不受影响");
            }
            if (collector.intoFound) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_FORBIDDEN_CLAUSE,
                        "检测到 SELECT ... INTO（写表）");
            }
            // 参数占位符放在 LIMIT 校验之后统一拒绝：
            // "LIMIT ?" 应由 LIMIT_INVALID(052) 给出精确原因，而不是笼统的 PARAMETER(048)
            result.check("AST 遍历：SELECT * / INTO 均未出现");

            // ---- 8. 关键字与函数黑名单（文本扫描前先 scrub 字符串字面量，避免误杀） ----
            String upper = scrubbed.toUpperCase();
            for (String keyword : rules.denyKeywords) {
                if (Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(upper).find()) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_DENY_KEYWORD, keyword);
                }
            }
            for (String function : collector.functions) {
                if (rules.denyFunctions.contains(function)) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_DENY_FUNCTION, function);
                }
            }
            result.check("禁用关键字 / 函数扫描通过（基线 " + rules.denyFunctions.size() + " 个函数）");

            // ---- 9. 复杂度上限 ----
            int maxSubqueries = positive(properties.getMaxSubqueries(), 3);
            int maxTables = positive(properties.getMaxTables(), 8);
            int maxColumns = positive(properties.getMaxColumns(), 80);
            if (collector.subqueryCount > maxSubqueries) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_COMPLEXITY_EXCEEDED,
                        "子查询/CTE " + collector.subqueryCount + " > " + maxSubqueries);
            }
            if (tables.size() > maxTables) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_COMPLEXITY_EXCEEDED,
                        "表数 " + tables.size() + " > " + maxTables);
            }
            if (collector.columns.size() > maxColumns) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_COMPLEXITY_EXCEEDED,
                        "列数 " + collector.columns.size() + " > " + maxColumns);
            }
            if (collector.maxJoins > rules.maxJoins) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_COMPLEXITY_EXCEEDED,
                        "JOIN " + collector.maxJoins + " > " + rules.maxJoins);
            }
            result.check("复杂度：子查询 " + collector.subqueryCount + " / 表 " + tables.size()
                    + " / 列 " + collector.columns.size() + " / JOIN " + collector.maxJoins + "，均在限内");

            // ---- 10. 表白名单 ----
            if (tables.isEmpty()) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_UNSUPPORTED_STATEMENT,
                        "SQL 未引用任何白名单物理表（常量查询 / 纯函数调用不予执行）");
            }
            for (String table : tables) {
                String key = SchemaWhitelist.normalize(table);
                if (!whitelist.getTableLookup().containsKey(key)) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_TABLE_NOT_ALLOWED, table);
                }
                if (key.indexOf('.') < 0 && whitelist.getAmbiguousTableNames().contains(key)) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_TABLE_NOT_ALLOWED,
                            table + " 存在同名 schema，必须写成 schema.table");
                }
            }

            // ---- 11. 列白名单（含 forbid 敏感列；SELECT 别名在 ORDER BY 中放行） ----
            whitelist.getSelectAliases().addAll(collector.aliases);
            for (String column : collector.columns) {
                if (whitelist.getForbiddenColumns().contains(column)) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_COLUMN_NOT_ALLOWED,
                            column + " 命中 mask_rule=forbid");
                }
                if (!whitelist.getColumnLookup().contains(column)
                        && !whitelist.getSelectAliases().contains(column)) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_COLUMN_NOT_ALLOWED, column);
                }
            }
            result.check("表列白名单校验通过：引用表 " + tables.size()
                    + " 张、列 " + collector.columns.size() + " 个");

            // ---- 12. 行数上限 / 超时 / 只读通道 ----
            int maxRows = resolveMaxRows(ds);
            int queryTimeoutMs = resolveTimeout(ds);
            boolean readonlyChannel = ds.getReadonlyEnabled() != null && ds.getReadonlyEnabled() == 1
                    && StringUtils.hasText(ds.getReadonlyUrl());
            if (!readonlyChannel) {
                result.warn("只读通道未启用或未配置 readonly_url，S7 将降级复用主连接"
                        + "（仍强制 setReadOnly + AST 只读 + LIMIT），建议尽快配置只读账号");
            }

            // ---- 13. 强制 LIMIT（安全红线：require_limit=false 也不放行） ----
            Limit limit = select.getLimit();
            if (limit == null) {
                select.setLimit(new Limit().withRowCount(new LongValue(maxRows)));
                result.setLimitApplied(true);
                result.check("LIMIT：原 SQL 缺失，已强制追加 LIMIT " + maxRows);
            } else {
                Expression rowCount = limit.getRowCount();
                if (!(rowCount instanceof LongValue)) {
                    String kind = rowCount == null ? "null"
                            : (rowCount instanceof AllValue ? "LIMIT ALL" : rowCount.getClass().getSimpleName());
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_LIMIT_INVALID, kind);
                }
                long value = ((LongValue) rowCount).getValue();
                if (value <= 0) {
                    reject(DataSourceErrorCodeConstants.SQL_GUARD_LIMIT_INVALID, "LIMIT " + value);
                }
                if (value > maxRows) {
                    select.setLimit(new Limit().withRowCount(new LongValue(maxRows)));
                    result.setLimitClamped(true);
                    result.check("LIMIT：原值 " + value + " 超过 max_rows，已收敛为 " + maxRows);
                } else {
                    result.check("LIMIT：原值 " + value + " ≤ max_rows " + maxRows + "，保留");
                }
            }

            // ---- 14. 其余参数占位符（LIMIT ? 已在上面给出精确原因） ----
            if (collector.paramCount > 0) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_PARAMETER_NOT_ALLOWED,
                        "占位符 " + collector.paramCount + " 处");
            }
            result.check("参数占位符：无（静态闸门不接受未绑定参数的 SQL）");

            // ---- 15. 产出 SafeSql ----
            String executableSql = select.toString();
            SafeSql safeSql = new SafeSql();
            safeSql.setDatasourceId(ds.getId());
            safeSql.setOriginalSql(sql);
            safeSql.setExecutableSql(executableSql);
            safeSql.setSqlHash(sha256(executableSql));
            safeSql.setMaxRows(maxRows);
            safeSql.setQueryTimeoutMs(queryTimeoutMs);
            safeSql.setReadonlyChannel(readonlyChannel);
            safeSql.setStructVersion(ds.getStructVersion());
            safeSql.setTables(new ArrayList<>(tables));
            safeSql.setColumns(new ArrayList<>(collector.columns));
            // 需脱敏列 + 它的投影别名都要进 SafeSql：
            // "SELECT secret_col AS s" 的结果列标签是 s，只按原列名匹配会漏脱敏
            Set<String> maskColumns = new LinkedHashSet<>();
            for (String column : collector.columns) {
                if (!whitelist.getMaskColumns().containsKey(column)) {
                    continue;
                }
                maskColumns.add(column);
                Set<String> aliases = collector.projectionAliases.get(column);
                if (aliases != null) {
                    maskColumns.addAll(aliases);
                }
            }
            safeSql.setMaskColumns(new ArrayList<>(maskColumns));

            result.setPassed(true);
            result.setSafeSql(safeSql);
            result.setRecallMode(schemaContext != null ? schemaContext.getRecallMode() : "AUTO_RECALL");
            result.check("校验通过：SafeSql 已生成，SHA-256=" + safeSql.getSqlHash().substring(0, 16) + "…");
            return result;
        } catch (GuardReject ex) {
            result.setOriginalSql(sql);
            result.setErrorCode(ex.code);
            result.setErrorMsg(ex.getMessage());
            result.check("REJECT [" + ex.code + "] " + ex.getMessage());
            return result;
        } catch (ServiceException ex) {
            result.setOriginalSql(sql);
            result.setErrorCode(ex.getCode());
            result.setErrorMsg(ex.getMessage());
            result.check("REJECT [" + ex.getCode() + "] " + ex.getMessage());
            return result;
        } catch (Exception ex) {
            log.error("[sqlGuard][validate][unexpected] datasourceId={}", datasourceId, ex);
            result.setOriginalSql(sql);
            result.setErrorCode(DataSourceErrorCodeConstants.SQL_GUARD_PARSE_FAILED.getCode());
            result.setErrorMsg("校验器内部异常，已拒绝执行：" + brief(ex));
            return result;
        }
    }

    // ==================== SQL 抽取 ====================

    private String extractSql(String rawText, SqlGuardResult result) {
        if (rawText == null) {
            return "";
        }
        String text = rawText.trim();
        Matcher matcher = CODE_BLOCK.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).trim();
            if (matcher.find()) {
                result.warn("检测到多个 markdown 代码块，仅取第一个，其余内容忽略");
            }
        }
        // 允许末尾一个分号；中间分号保留，交给 parseStatements() 判多语句
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }

    /**
     * 供「关键字 / 行锁文本扫描」使用的净化文本（不用于最终执行）：
     * 1. 先把字符串字面量替换成 '?'，避免 WHERE note='DROP TABLE' 被误杀；
     * 2. 再移除块注释与行注释，避免注释里的 DROP / FOR UPDATE 被误杀；
     * 3. 顺序不能反——先去注释会把字符串中的 "/*" 当成注释起点。
     */
    private static String scrubLiterals(String sql) {
        String scrubbed = sql.replaceAll("'(?:[^']|'')*'", "'?'");
        scrubbed = scrubbed.replaceAll("(?s)/\\*.*?\\*/", " ");
        return scrubbed.replaceAll("--[^\\r\\n]*", " ");
    }

    // ==================== S3 白名单：服务端重载 + 取交集 ====================

    private SchemaWhitelist loadWhitelist(AiDatasource ds, String question, SchemaContext provided,
                                          SqlGuardResult result) {
        SchemaContext context = provided;
        if (context == null) {
            if (!StringUtils.hasText(question)) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_WHITELIST_MISSING,
                        "既无 schemaContext 也无 question");
            }
            try {
                context = schemaRecallService.recall(ds.getId(), question, null);
            } catch (Exception ex) {
                reject(DataSourceErrorCodeConstants.SQL_GUARD_WHITELIST_MISSING,
                        "服务端自动召回失败：" + brief(ex));
            }
            result.warn("未提供 schemaContext，已由服务端按 question 自动执行 S3 召回");
        }
        if (context == null || context.getTableIds() == null || context.getTableIds().isEmpty()) {
            reject(DataSourceErrorCodeConstants.SQL_GUARD_WHITELIST_EMPTY, "召回 tableIds 为空");
        }
        Set<Long> recallTableIds = SchemaWhitelist.toIdSet(context.getTableIds());
        Set<Long> recallColumnIds = SchemaWhitelist.toIdSet(context.getColumnIds());
        if (recallColumnIds.isEmpty()) {
            reject(DataSourceErrorCodeConstants.SQL_GUARD_WHITELIST_EMPTY, "召回 columnIds 为空");
        }
        result.setRecallMode(context.getRecallMode());

        Long tenantId = TenantContextHolder.getTenantId();
        List<AiDatasourceTable> allTables = tableMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceTable>()
                        .eq(AiDatasourceTable::getDatasourceId, ds.getId()));
        List<AiDatasourceColumn> allColumns = columnMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceColumn>()
                        .eq(AiDatasourceColumn::getDatasourceId, ds.getId()));

        SchemaWhitelist whitelist = new SchemaWhitelist();
        whitelist.setDatasourceId(ds.getId());
        whitelist.setStructVersion(ds.getStructVersion());

        // 先把召回 ID 与 PG 元数据取交集（前端伪造 ID 无法放行本租户不可见的表）
        List<AiDatasourceTable> usableTables = new ArrayList<>();
        for (AiDatasourceTable table : allTables) {
            if (!usableTable(table, tenantId) || !recallTableIds.contains(table.getId())) {
                continue;
            }
            usableTables.add(table);
        }
        Map<String, Integer> simpleNameCount = new HashMap<>();
        for (AiDatasourceTable table : usableTables) {
            String simple = simpleTableName(table.getTableName());
            simpleNameCount.merge(simple, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : simpleNameCount.entrySet()) {
            if (entry.getValue() > 1) {
                whitelist.getAmbiguousTableNames().add(entry.getKey());
            }
        }
        for (AiDatasourceTable table : usableTables) {
            String simple = simpleTableName(table.getTableName());
            String schema = SchemaWhitelist.normalize(table.getSchemaName());
            String full = StringUtils.hasText(schema) ? schema + "." + simple : simple;
            whitelist.getTableNames().add(full);
            whitelist.getTableLookup().put(full, table.getId());
            whitelist.getTableLookup().put(simple, table.getId());
        }

        Set<Long> usableTableIds = new LinkedHashSet<>();
        for (AiDatasourceTable table : usableTables) {
            usableTableIds.add(table.getId());
        }
        // forbid 列先单独登记，保证任何表都引用不到
        for (AiDatasourceColumn column : allColumns) {
            if (!belongsToTenant(column.getTenantId(), tenantId) || !usableTableIds.contains(column.getTableId())) {
                continue;
            }
            if ("forbid".equalsIgnoreCase(trim(column.getMaskRule()))) {
                whitelist.getForbiddenColumns().add(SchemaWhitelist.normalize(column.getColumnName()));
            }
        }
        for (AiDatasourceColumn column : allColumns) {
            if (!belongsToTenant(column.getTenantId(), tenantId)
                    || !usableTableIds.contains(column.getTableId())
                    || !recallColumnIds.contains(column.getId())
                    || !usableColumn(column)
                    || whitelist.getForbiddenColumns().contains(SchemaWhitelist.normalize(column.getColumnName()))) {
                continue;
            }
            String name = SchemaWhitelist.normalize(column.getColumnName());
            whitelist.getColumnNames().add(name);
            whitelist.getColumnLookup().add(name);
            String maskRule = trim(column.getMaskRule());
            boolean sensitive = column.getSensitiveType() != null && column.getSensitiveType() > 0;
            if (StringUtils.hasText(maskRule) || (sensitive && Boolean.TRUE.equals(properties.getMaskSensitiveByDefault()))) {
                whitelist.getMaskColumns().put(name, StringUtils.hasText(maskRule) ? maskRule : "default_mask");
            }
        }
        if (whitelist.isEmpty()) {
            reject(DataSourceErrorCodeConstants.SQL_GUARD_WHITELIST_EMPTY,
                    "召回 ID 与 PG 可见可查元数据无交集（可能元数据已下线或跨租户伪造 ID）");
        }
        return whitelist;
    }

    private static boolean usableTable(AiDatasourceTable table, Long tenantId) {
        return belongsToTenant(table.getTenantId(), tenantId)
                && table.getVisible() != null && table.getVisible() == 1
                && table.getQueryable() != null && table.getQueryable() == 1
                && (table.getSyncStatus() == null || table.getSyncStatus() != SYNC_STATUS_RETIRED)
                && (table.getDeleted() == null || table.getDeleted() == 0);
    }

    private static boolean usableColumn(AiDatasourceColumn column) {
        return column.getVisible() != null && column.getVisible() == 1
                && column.getQueryable() != null && column.getQueryable() == 1
                && (column.getSyncStatus() == null || column.getSyncStatus() != SYNC_STATUS_RETIRED)
                && (column.getDeleted() == null || column.getDeleted() == 0)
                && !"forbid".equalsIgnoreCase(trim(column.getMaskRule()));
    }

    private static boolean belongsToTenant(Long actual, Long expected) {
        return expected != null && Objects.equals(actual, expected);
    }

    private static String simpleTableName(String raw) {
        String name = SchemaWhitelist.normalize(raw);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    // ==================== sql_rules ====================

    private RuleSet parseRules(AiDatasource ds, SqlGuardResult result) {
        RuleSet rules = new RuleSet();
        rules.denyKeywords = new LinkedHashSet<>(BASE_DENY_KEYWORDS);
        rules.denyFunctions = new LinkedHashSet<>(BASE_DENY_FUNCTIONS);
        rules.maxJoins = positive(properties.getMaxJoins(), 5);
        rules.requireLimit = true;
        if (properties.getExtraDenyKeywords() != null) {
            for (String item : properties.getExtraDenyKeywords()) {
                if (StringUtils.hasText(item)) {
                    rules.denyKeywords.add(item.trim().toUpperCase());
                }
            }
        }
        if (properties.getExtraDenyFunctions() != null) {
            for (String item : properties.getExtraDenyFunctions()) {
                if (StringUtils.hasText(item)) {
                    rules.denyFunctions.add(normalizeFunction(item));
                }
            }
        }
        if (!StringUtils.hasText(ds.getSqlRules())) {
            result.warn("ai_datasource.sql_rules 为空，已按内置安全基线校验");
            return rules;
        }
        try {
            JsonNode node = MAPPER.readTree(ds.getSqlRules());
            if (node.path("max_joins").isInt()) {
                rules.maxJoins = Math.min(rules.maxJoins, Math.max(0, node.path("max_joins").asInt()));
            }
            if (node.has("require_limit")) {
                rules.requireLimit = node.path("require_limit").asBoolean();
            }
            for (JsonNode item : node.path("deny_keywords")) {
                if (StringUtils.hasText(item.asText())) {
                    rules.denyKeywords.add(item.asText().trim().toUpperCase());
                }
            }
            for (JsonNode item : node.path("deny_functions")) {
                if (StringUtils.hasText(item.asText())) {
                    rules.denyFunctions.add(normalizeFunction(item.asText()));
                }
            }
            if (!rules.requireLimit) {
                result.warn("sql_rules.require_limit=false，但安全红线不可配置关闭，仍强制 LIMIT ≤ max_rows");
            }
            result.check("sql_rules 已解析：deny_keywords=" + rules.denyKeywords.size()
                    + " / deny_functions=" + rules.denyFunctions.size()
                    + " / max_joins=" + rules.maxJoins + " / require_limit=" + rules.requireLimit);
        } catch (Exception ex) {
            result.warn("sql_rules 无法解析，已按内置安全基线校验：" + brief(ex));
        }
        return rules;
    }

    private static String normalizeFunction(String raw) {
        String name = SchemaWhitelist.normalize(raw);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    // ==================== LIMIT / 超时 / 工具 ====================

    private int resolveMaxRows(AiDatasource ds) {
        int configured = positive(properties.getDefaultMaxRows(), 1000);
        int dsMax = ds.getMaxRows() == null || ds.getMaxRows() <= 0 ? configured : ds.getMaxRows();
        return Math.max(1, Math.min(configured, dsMax));
    }

    private int resolveTimeout(AiDatasource ds) {
        int configured = positive(properties.getMaxQueryTimeoutMs(), 10000);
        int dsTimeout = ds.getQueryTimeoutMs() == null || ds.getQueryTimeoutMs() <= 0
                ? configured : ds.getQueryTimeoutMs();
        return Math.max(1, Math.min(configured, dsTimeout));
    }

    private static boolean hasForbiddenClause(Select select) {
        return select.getForMode() != null
                || select.getForClause() != null
                || select.getForUpdateTable() != null
                || select.isNoWait()
                || select.isSkipLocked()
                || select.getWait() != null
                || select.getFetch() != null;
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String brief(Throwable ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String first = message.split("\\r?\\n")[0];
        return first.length() > 160 ? first.substring(0, 160) : first;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }

    private static void reject(cn.iocoder.yudao.framework.common.exception.ErrorCode errorCode, String detail) {
        String message = detail == null ? errorCode.getMsg() : errorCode.getMsg() + "：" + detail;
        throw new GuardReject(errorCode.getCode(), message);
    }

    /** 内部短路异常：把结构性拒绝转成 SqlGuardResult，而不是 HTTP 500 */
    private static final class GuardReject extends RuntimeException {

        private final int code;

        private GuardReject(int code, String message) {
            super(message);
            this.code = code;
        }

    }

    private static final class RuleSet {

        private Set<String> denyKeywords;
        private Set<String> denyFunctions;
        private int maxJoins;
        private boolean requireLimit;

    }

    /** AST 遍历收集器：只调用一次 getTables()，内部所有集合均去重 */
    private static final class Collector extends TablesNamesFinder<Void> {

        private final Set<String> columns = new LinkedHashSet<>();
        private final Set<String> functions = new LinkedHashSet<>();
        private final Set<String> aliases = new LinkedHashSet<>();
        private final Map<String, Set<String>> projectionAliases = new HashMap<>();
        private final Set<PlainSelect> visitedPlain =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<ParenthesedSelect> visitedParenthesed =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private int subqueryCount;
        private int maxJoins;
        private int paramCount;
        private boolean selectStarFound;
        private boolean intoFound;

        @Override
        public <S> Void visit(Column column, S context) {
            columns.add(SchemaWhitelist.normalize(column.getColumnName()));
            return super.visit(column, context);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            functions.add(normalizeFunction(function.getName()));
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(JdbcParameter parameter, S context) {
            paramCount++;
            return super.visit(parameter, context);
        }

        @Override
        public <S> Void visit(PlainSelect plainSelect, S context) {
            if (visitedPlain.add(plainSelect)) {
                maxJoins = Math.max(maxJoins,
                        plainSelect.getJoins() == null ? 0 : plainSelect.getJoins().size());
                if ((plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty())
                        || plainSelect.getIntoTempTable() != null) {
                    intoFound = true;
                }
                if (plainSelect.getSelectItems() != null) {
                    for (SelectItem<?> item : plainSelect.getSelectItems()) {
                        Expression expression = item.getExpression();
                        if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
                            selectStarFound = true;
                        }
                        if (item.getAliasName() != null) {
                            String alias = SchemaWhitelist.normalize(item.getAliasName());
                            aliases.add(alias);
                            if (expression instanceof Column) {
                                projectionAliases
                                        .computeIfAbsent(SchemaWhitelist.normalize(
                                                ((Column) expression).getColumnName()),
                                                key -> new LinkedHashSet<>())
                                        .add(alias);
                            }
                        }
                    }
                }
            }
            return super.visit(plainSelect, context);
        }

        @Override
        public <S> Void visit(ParenthesedSelect parenthesedSelect, S context) {
            if (visitedParenthesed.add(parenthesedSelect)) {
                subqueryCount++;
            }
            return super.visit(parenthesedSelect, context);
        }

        @Override
        public <S> Void visit(AllColumns allColumns, S context) {
            // 不在此处判 SELECT *：count(*) 内部的 AllColumns 会被误杀，改由 SelectItem 直接类型判定
            return super.visit(allColumns, context);
        }

        @Override
        public <S> Void visit(AllTableColumns allTableColumns, S context) {
            return super.visit(allTableColumns, context);
        }

    }

}
