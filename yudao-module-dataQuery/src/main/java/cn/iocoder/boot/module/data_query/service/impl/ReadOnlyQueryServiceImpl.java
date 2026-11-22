package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiQueryAudit;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.framework.datasource.DatasourceJdbcSupport;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryAuditContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SafeSql;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SchemaWhitelist;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SqlGuardProperties;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SqlGuardResult;
import cn.iocoder.boot.module.data_query.service.AiQueryAuditService;
import cn.iocoder.boot.module.data_query.service.ReadOnlyQueryService;
import cn.iocoder.boot.module.data_query.service.SqlGuardService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SQL_GUARD_CONNECTION_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SQL_GUARD_EXECUTE_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SQL_GUARD_QUERY_TIMEOUT;

/**
 * S7 只读执行器实现。
 *
 * 三重只读保证：S6 AST 只允许单条 SELECT（无 INTO / 行锁 / DDL）、JDBC 连接
 * setReadOnly(true)、独立只读账号 / readonly_url。只读通道未配置时降级复用主连接，
 * 但会写告警并落审计，绝不在报告里假装「已走只读通道」。
 *
 * 四重行数 / 资源约束：SQL 强制 LIMIT ≤ max_rows、Statement#setMaxRows、
 * setQueryTimeout（JDBC 标准，PG 驱动下发 statement_timeout）、setFetchSize。
 *
 * 结果不进公共缓存；审计由 P2-B 的 ai_query_audit 表 append-only 留痕，
 * 成功、校验拒绝、执行失败三种终态都会落一条，错误摘要脱敏（不回显连接串 / 密码）。
 */
@Slf4j
@Service
public class ReadOnlyQueryServiceImpl implements ReadOnlyQueryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_REJECT = 3;
    private static final int STATUS_FAILED = 5;

    @Resource
    private SqlGuardService sqlGuardService;
    @Resource
    private AiDatasourceOwnerValidator ownerValidator;
    @Resource
    private AiQueryAuditService auditService;
    @Resource
    private SqlGuardProperties properties;

    @Override
    public QueryResult execute(Long datasourceId, String rawText, String question,
                               SchemaContext schemaContext) {
        return execute(datasourceId, rawText, question, schemaContext, null);
    }

    @Override
    public QueryResult execute(Long datasourceId, String rawText, String question,
                               SchemaContext schemaContext, QueryAuditContext auditContext) {
        long start = System.currentTimeMillis();
        String traceId = auditContext != null && auditContext.getTraceId() != null
                && !auditContext.getTraceId().isBlank()
                ? auditContext.getTraceId()
                : UUID.randomUUID().toString().replace("-", "");

        // ---- 1. S6 闸门：拿不到 SafeSql 就绝不打开目标数据源连接 ----
        SqlGuardResult guard = sqlGuardService.validate(datasourceId, rawText, question, schemaContext);
        if (!Boolean.TRUE.equals(guard.getPassed()) || guard.getSafeSql() == null) {
            writeAudit(datasourceId, question, schemaContext, guard, null, traceId, start,
                    STATUS_REJECT, String.valueOf(guard.getErrorCode()), guard.getErrorMsg(), auditContext);
            throw new ServiceException(guard.getErrorCode(), guard.getErrorMsg());
        }
        SafeSql safeSql = guard.getSafeSql();
        AiDatasource ds = ownerValidator.requireOwned(datasourceId);

        QueryResult result = new QueryResult();
        result.setTraceId(traceId);
        result.setExecutedSql(safeSql.getExecutableSql());
        result.setRecallMode(guard.getRecallMode());
        result.setReadonlyConnection(safeSql.getReadonlyChannel());
        result.setWarnings(new ArrayList<>(guard.getWarnings()));
        result.setTruncated(false);
        result.setMasked(false);

        // ---- 2. 独立只读连接（用完即关，不占业务连接池） ----
        Connection connection;
        try {
            connection = DatasourceJdbcSupport.open(ds, true);
        } catch (Exception ex) {
            log.warn("[readOnlyQuery][connect] datasourceId={}, traceId={}, reason={}",
                    datasourceId, traceId, ex.getMessage());
            String message = SQL_GUARD_CONNECTION_FAILED.getMsg() + "：" + sanitize(ex);
            writeAudit(datasourceId, question, schemaContext, guard, result, traceId, start,
                    STATUS_FAILED, String.valueOf(SQL_GUARD_CONNECTION_FAILED.getCode()), message, auditContext);
            throw new ServiceException(SQL_GUARD_CONNECTION_FAILED.getCode(), message);
        }

        try (Connection conn = connection) {
            conn.setReadOnly(true);
            try (Statement statement = conn.createStatement()) {
                int timeoutSeconds = Math.max(1, (safeSql.getQueryTimeoutMs() + 999) / 1000);
                statement.setQueryTimeout(timeoutSeconds);
                statement.setMaxRows(safeSql.getMaxRows());
                statement.setFetchSize(Math.max(1, properties.getFetchSize() == null ? 500 : properties.getFetchSize()));
                result.setWarnings(new ArrayList<>(guard.getWarnings()));
                result.getWarnings().add("只读约束：setReadOnly=true / LIMIT " + safeSql.getMaxRows()
                        + " / setMaxRows / queryTimeout=" + timeoutSeconds + "s"
                        + (Boolean.TRUE.equals(safeSql.getReadonlyChannel())
                            ? " / 独立只读通道" : " / 主连接降级（建议配置只读账号）"));

                try (ResultSet rs = statement.executeQuery(safeSql.getExecutableSql())) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> columnNames = new ArrayList<>();
                    List<String> columnTypes = new ArrayList<>();
                    Set<String> maskColumns = new HashSet<>();
                    for (String name : safeSql.getMaskColumns()) {
                        maskColumns.add(SchemaWhitelist.normalize(name));
                    }
                    for (int i = 1; i <= columnCount; i++) {
                        columnNames.add(meta.getColumnLabel(i));
                        columnTypes.add(meta.getColumnTypeName(i));
                    }
                    result.setColumnNames(columnNames);
                    result.setColumnTypes(columnTypes);

                    String placeholder = properties.getMaskPlaceholder() == null
                            ? "******" : properties.getMaskPlaceholder();
                    List<List<Object>> rows = new ArrayList<>();
                    int maxRows = safeSql.getMaxRows();
                    while (rows.size() < maxRows && rs.next()) {
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            String label = SchemaWhitelist.normalize(meta.getColumnLabel(i));
                            if (maskColumns.contains(label)) {
                                row.add(placeholder);
                                result.setMasked(true);
                            } else {
                                row.add(toCell(rs.getObject(i)));
                            }
                        }
                        rows.add(row);
                    }
                    result.setRows(rows);
                    result.setRowCount(rows.size());
                    // setMaxRows + SQL LIMIT 都可能刚好截断，行数触顶即标「可能还有更多」
                    result.setTruncated(rows.size() >= maxRows);
                }
            }
        } catch (SQLException ex) {
            log.warn("[readOnlyQuery][execute] datasourceId={}, traceId={}, state={}, reason={}",
                    datasourceId, traceId, ex.getSQLState(), ex.getMessage());
            boolean timeout = isTimeout(ex);
            int code = timeout ? SQL_GUARD_QUERY_TIMEOUT.getCode() : SQL_GUARD_EXECUTE_FAILED.getCode();
            String message = (timeout ? SQL_GUARD_QUERY_TIMEOUT.getMsg() : SQL_GUARD_EXECUTE_FAILED.getMsg())
                    + "：" + sanitize(ex);
            writeAudit(datasourceId, question, schemaContext, guard, result, traceId, start,
                    STATUS_FAILED, String.valueOf(code), message, auditContext);
            throw new ServiceException(code, message);
        } catch (Exception ex) {
            log.warn("[readOnlyQuery][unexpected] datasourceId={}, traceId={}, reason={}",
                    datasourceId, traceId, ex.getMessage());
            String message = SQL_GUARD_EXECUTE_FAILED.getMsg() + "：" + sanitize(ex);
            writeAudit(datasourceId, question, schemaContext, guard, result, traceId, start,
                    STATUS_FAILED, String.valueOf(SQL_GUARD_EXECUTE_FAILED.getCode()), message, auditContext);
            throw new ServiceException(SQL_GUARD_EXECUTE_FAILED.getCode(), message);
        }

        result.setLatencyMs(System.currentTimeMillis() - start);
        result.setAuditId(writeAudit(datasourceId, question, schemaContext, guard, result,
                traceId, start, STATUS_SUCCESS, null, null, auditContext));
        return result;
    }

    // ==================== 审计留痕（append-only） ====================

    private Long writeAudit(Long datasourceId, String question, SchemaContext context,
                            SqlGuardResult guard, QueryResult result, String traceId,
                            long start, int status, String errorCode, String errorMsg,
                            QueryAuditContext auditContext) {
        try {
            AiQueryAudit audit = new AiQueryAudit();
            audit.setSessionId(auditContext == null || auditContext.getSessionId() == null
                    ? 0L : auditContext.getSessionId());
            audit.setUserId(SecurityFrameworkUtils.getLoginUserId());
            audit.setDatasourceId(datasourceId);
            audit.setTraceId(traceId);
            audit.setQuestion(question);
            String normalized = auditContext != null && auditContext.getNormalizedQuestion() != null
                    ? auditContext.getNormalizedQuestion()
                    : (context == null ? question : context.getQuestion());
            audit.setNormalizedQuestion(normalized);
            if (auditContext != null) {
                audit.setIntentCode(auditContext.getIntentCode());
                audit.setPromptVersionId(auditContext.getPromptVersionId());
                audit.setModelConfigId(auditContext.getModelConfigId());
            }
            audit.setRecalledTables(toJson(guard.getWhitelistTables()));
            audit.setGeneratedSql(guard.getOriginalSql());
            audit.setValidatedSql(guard.getSafeSql() == null ? null : guard.getSafeSql().getExecutableSql());
            audit.setExecutedSql(result == null ? null : result.getExecutedSql());
            audit.setSqlStatus(guard.getSafeSql() == null ? "REJECT" : "PASS");
            audit.setResultRows(result == null || result.getRowCount() == null
                    ? 0L : result.getRowCount().longValue());
            audit.setResultTruncated(result != null && Boolean.TRUE.equals(result.getTruncated()) ? 1 : 0);
            audit.setLatencyMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start));
            audit.setStatus(status);
            audit.setErrorCode(errorCode);
            audit.setErrorMsg(errorMsg == null ? null : clip(errorMsg, 1000));
            audit.setReadonlyConnection(result != null && Boolean.TRUE.equals(result.getReadonlyConnection()) ? 1 : 0);
            audit.setClientIp(clientIp());
            return auditService.createAiQueryAudit(audit);
        } catch (Exception ex) {
            // 审计失败不能反过来把一次成功查询变成 500，但必须留日志
            log.error("[readOnlyQuery][audit][failed] datasourceId={}, traceId={}", datasourceId, traceId, ex);
            return null;
        }
    }

    private static String toJson(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static String clientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes == null ? "" : clip(attributes.getRequest().getRemoteAddr(), 64);
        } catch (Exception ex) {
            return "";
        }
    }

    // ==================== 错误分类与脱敏 ====================

    private static boolean isTimeout(SQLException ex) {
        String state = ex.getSQLState();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return "57014".equals(state)
                || message.contains("statement timeout")
                || message.contains("canceling statement")
                || message.contains("query timeout")
                || message.contains("timed out");
    }

    /** 错误摘要：屏蔽连接串 / 密码，过滤不可读字符（PG 中文错误在部分 JDBC 环境会乱码） */
    private static String sanitize(Throwable ex) {
        String raw = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String lower = raw.toLowerCase();
        if (lower.contains("jdbc:") || lower.contains("password") || lower.contains("connection to ")) {
            raw = "连接或执行异常，详见服务端日志（连接串与凭证不回显）";
        }
        StringBuilder sb = new StringBuilder(ex.getClass().getSimpleName()).append(": ");
        for (int i = 0; i < raw.length() && sb.length() < 260; i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else if (c >= 0x20 && c <= 0x7e) {
                sb.append(c);
            } else if (c >= 0x4e00 && c <= 0x9fff) {
                sb.append(c);
            } else if ("，。；：（）【】、！？“”‘’".indexOf(c) >= 0) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static Object toCell(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return "[binary]";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

}
