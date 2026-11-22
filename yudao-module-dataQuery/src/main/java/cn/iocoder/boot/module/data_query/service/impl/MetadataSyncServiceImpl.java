package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataPreviewRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncStatusRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataVisibilityReqVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceSyncLog;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTableRelation;
import cn.iocoder.boot.module.data_query.entity.AiSchemaVector;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.framework.datasource.DatasourceJdbcSupport;
import cn.iocoder.boot.module.data_query.framework.datasource.MetadataSnapshot;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceColumnMapper;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceMapper;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceTableMapper;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceTableRelationMapper;
import cn.iocoder.boot.module.data_query.mapper.AiSchemaVectorMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceSyncLogService;
import cn.iocoder.boot.module.data_query.service.MetadataSyncService;
import cn.iocoder.boot.module.data_query.service.SchemaVectorIndexSyncService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_SYNC_NO_TABLE;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_VISIBILITY_IDS_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 元数据同步实现（P1）
 *
 * 设计取舍：
 * 1. 用 JDBC DatabaseMetaData 而不是手写 information_schema SQL：同一套代码兼容 PG / MySQL 等方言；
 *    优先走只读连接通道，且连接 setReadOnly(true)。
 * 2. 【绝不覆盖人工补充字段】：name_zh / description / metric_def / synonyms / visible /
 *    queryable / mask_rule / sensitive_type / sort / remark 只在新增行时给默认值，
 *    已存在的行只更新技术字段（类型、长度、主键、指纹、同步状态、时间）。
 *    自动同步不能把人工录入的业务语义冲掉——元数据质量决定整个问数系统上限。
 * 3. struct_hash 比对：表指纹 = 列名/类型/可空/主键的有序拼接，列指纹 = 列自身结构。
 *    变更的行标 sync_status = 3（结构已变更，等管理端人工确认），源端消失的行标 4（失效）。
 *    有任意变更才推进 ai_datasource.struct_version，向量索引据此判断新鲜度。
 * 4. 不采集业务数据行：没有 SELECT DISTINCT、没有 sample_rows 采样，外键只来自元数据。
 *    外键推断出的 Join 路径默认 enabled = 0 / verified = 0，必须人工验证后才可用。
 * 5. 同步是幂等的、可重跑的：失败不抛 500，写失败日志并返回可读结果对象，下次重跑补齐。
 */
@Service
public class MetadataSyncServiceImpl implements MetadataSyncService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 无条件排除的系统 schema，避免同步出成千上万条系统表 */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "pg_catalog", "pg_toast", "pg_toast_temp_1",
            "pg_temp_1", "pg_temp", "sys", "mysql", "performance_schema");

    /** 无条件排除的表名前缀 */
    private static final List<String> SYSTEM_TABLE_PREFIXES = List.of("pg_", "sql_", "_tmp", "tmp_", "temp_");

    private static final int MAX_CHANGE_DETAIL = 2000;

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;
    @Resource
    private AiDatasourceMapper datasourceMapper;
    @Resource
    private AiDatasourceTableMapper tableMapper;
    @Resource
    private AiDatasourceColumnMapper columnMapper;
    @Resource
    private AiDatasourceTableRelationMapper relationMapper;
    @Resource
    private AiSchemaVectorMapper vectorMapper;
    @Resource
    private AiDatasourceSyncLogService syncLogService;
    @Resource
    private SchemaVectorIndexSyncService schemaVectorIndexSyncService;

    // ==================== 同步主流程 ====================

    @Override
    public MetadataSyncRespVO sync(Long datasourceId, boolean full) {
        return sync(ownerValidator.requireOwned(datasourceId), full);
    }

    /**
     * 用「调用方给定的连接与过滤配置」同步。
     *
     * 一键导入用它把本次请求的扫描范围（schema 白名单 / 表名 include / exclude）直接作用于同步，
     * 而不是回读数据库里的数据源配置——否则预览按新范围扫描、同步却按旧范围落库，两者对不上。
     * 入参必须是已通过归属校验的实体，且 id 非空（同步日志与主表状态需要写入该数据源）。
     */
    @Override
    public MetadataSyncRespVO sync(AiDatasource ds, boolean full) {
        if (ds == null || ds.getId() == null) {
            throw new IllegalArgumentException("同步数据源不能为空，且必须带有数据源编号");
        }
        Long datasourceId = ds.getId();
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        long begin = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        String taskNo = "SYN" + System.currentTimeMillis();
        Counts counts = new Counts();
        List<String> warnings = new ArrayList<>();
        boolean success = false;
        boolean readonlyConnection = false;
        String errorMsg = "";

        try (Connection connection = DatasourceJdbcSupport.open(ds, true)) {
            readonlyConnection = connection.isReadOnly();
            CollectResult collected = collect(connection, ds, warnings);
            if (collected.tables.isEmpty()) {
                throw exception(METADATA_SYNC_NO_TABLE);
            }
            apply(ds, tenantId, collected, full, counts, warnings);
            success = true;
        } catch (Exception ex) {
            errorMsg = safeError(ex);
            warnings.add("同步中断：" + errorMsg);
        }

        int before = ds.getStructVersion() == null ? 0 : ds.getStructVersion();
        int after = success && counts.hasChanges() ? before + 1 : before;
        LocalDateTime endTime = LocalDateTime.now();
        long latencyMs = System.currentTimeMillis() - begin;

        // 主表运行时字段一律由服务端回写
        datasourceMapper.update(null, new LambdaUpdateWrapper<AiDatasource>()
                .eq(AiDatasource::getId, datasourceId)
                .set(AiDatasource::getSyncStatus, success ? 2 : 3)
                .set(AiDatasource::getLastSyncTime, endTime)
                .set(AiDatasource::getStructVersion, after));

        // 同步出新表 / 删掉旧表后，向量索引不会自己长出来：不重建就永远召不到新表。
        // 这里只触发增量构建、不批量标脏 —— content_hash 已能识别变化，全库标脏会把所有行强制重 embed。
        if (success && after != before) {
            schemaVectorIndexSyncService.onStructureChanged(datasourceId);
        }

        int logStatus = success ? (warnings.isEmpty() ? 1 : 2) : 3;
        writeLog(ds, tenantId, taskNo, full, startTime, endTime, latencyMs,
                logStatus, counts, before, after, errorMsg, warnings);

        MetadataSyncRespVO resp = new MetadataSyncRespVO();
        resp.setDatasourceId(datasourceId);
        resp.setTaskNo(taskNo);
        resp.setSyncType(full ? 2 : 3);
        resp.setSuccess(success);
        resp.setStatus(logStatus);
        resp.setTableAdded(counts.tableAdded);
        resp.setTableChanged(counts.tableChanged);
        resp.setTableRemoved(counts.tableRemoved);
        resp.setColumnAdded(counts.columnAdded);
        resp.setColumnChanged(counts.columnChanged);
        resp.setColumnRemoved(counts.columnRemoved);
        resp.setRelationAdded(counts.relationAdded);
        resp.setStructVersionBefore(before);
        resp.setStructVersionAfter(after);
        resp.setLatencyMs(latencyMs);
        resp.setErrorMsg(errorMsg);
        resp.setWarnings(warnings);
        resp.setReadonlyConnection(readonlyConnection);
        return resp;
    }

    // ==================== 只读预览（一键导入用） ====================

    /**
     * 与正式同步共用 collect() / SchemaFilter，保证「预览看到的」与「真正导入的」是同一套表列。
     * 全程只开一条只读 JDBC 连接，不写数据源主表、元数据表、向量表或同步日志。
     */
    @Override
    public MetadataPreviewRespVO preview(AiDatasource ds, int importMaxTables, int maxPreviewTables,
                                         int maxColumnsPerTable) {
        long begin = System.currentTimeMillis();
        MetadataPreviewRespVO resp = new MetadataPreviewRespVO();
        resp.setDialect(ds.getDialect());
        resp.setSchemaName(ds.getSchemaName());
        resp.setMaxTables(importMaxTables);
        resp.setMaxPreviewTables(maxPreviewTables);
        List<String> warnings = new ArrayList<>();
        try (Connection connection = DatasourceJdbcSupport.open(ds, true)) {
            CollectResult collected = collect(connection, ds, warnings);
            int columnCount = 0;
            List<MetadataPreviewRespVO.TableItem> items = new ArrayList<>();
            for (MetadataSnapshot.TableInfo table : collected.tables.values()) {
                columnCount += table.getColumns().size();
                if (items.size() >= maxPreviewTables) {
                    continue;
                }
                MetadataPreviewRespVO.TableItem item = new MetadataPreviewRespVO.TableItem();
                item.setSchemaName(table.getSchemaName());
                item.setTableName(table.getTableName());
                item.setTableNameZh(nameZhOf(table.getRemark()));
                item.setTableType(table.getTableType());
                item.setColumnCount(table.getColumns().size());
                item.setPrimaryKeys(new ArrayList<>(table.getPrimaryKeys()));
                List<String> columns = new ArrayList<>();
                List<String> columnNameZhs = new ArrayList<>();
                for (MetadataSnapshot.ColumnInfo column : table.getColumns()) {
                    if (columns.size() >= maxColumnsPerTable) {
                        break;
                    }
                    columns.add(column.getColumnName());
                    columnNameZhs.add(nameZhOf(column.getRemark()));
                }
                item.setColumns(columns);
                item.setColumnNameZhs(columnNameZhs);
                item.setColumnsTruncated(table.getColumns().size() > columns.size());
                items.add(item);
            }
            resp.setTables(items);
            resp.setTableCount(collected.tables.size());
            resp.setColumnCount(columnCount);
            resp.setRelationCount(collected.relations.size());
            resp.setTruncated(collected.tables.size() > items.size());
            resp.setSuccess(true);
        } catch (Exception ex) {
            resp.setSuccess(false);
            resp.setTableCount(0);
            resp.setColumnCount(0);
            resp.setRelationCount(0);
            resp.setTruncated(false);
            resp.setErrorMsg(safeError(ex));
            warnings.add("预览中断：" + resp.getErrorMsg());
        }
        resp.setWarnings(warnings);
        resp.setLatencyMs(System.currentTimeMillis() - begin);
        return resp;
    }

    // ==================== 采集 ====================

    /**
     * 按方言解析 JDBC 元数据调用的 (catalog, schemaPattern)。
     * - PostgreSQL / Kingbase / OpenGauss：catalog 为空，schema 才是真实 schema（如 public）。
     * - MySQL / SQLServer / DM / Oracle / ClickHouse / Hive：JDBC 把「数据库」当作 catalog，
     *   schema 恒为空；MySQL 驱动会直接忽略 schemaPattern，因此必须把数据库名放进 catalog，
     *   否则 getTables(catalog=null, schemaPattern=库名) 会返回【所有数据库】的表与视图。
     * effectiveSchema 用于过滤与落库口径：MySQL 的 TABLE_SCHEM 恒为 null，用数据库名兜底。
     */
    private static JdbcScope resolveScope(AiDatasource ds) {
        JdbcScope scope = new JdbcScope();
        String catalogName = trimToNull(ds.getCatalogName());
        String schemaName = trimToNull(ds.getSchemaName());
        String dialect = ds.getDialect() == null ? "" : ds.getDialect().toLowerCase(Locale.ROOT);
        boolean pgFamily = dialect.startsWith("postgre") || dialect.equals("kingbase")
                || dialect.equals("opengauss");
        if (pgFamily) {
            scope.catalog = null;
            scope.schemaPattern = schemaName; // 可能为 null -> 扫全部 schema
            scope.effectiveSchema = schemaName;
        } else {
            scope.catalog = catalogName != null ? catalogName : schemaName;
            scope.schemaPattern = null; // MySQL 等驱动忽略 schemaPattern
            scope.effectiveSchema = scope.catalog;
        }
        return scope;
    }

    private CollectResult collect(Connection connection, AiDatasource ds, List<String> warnings)
            throws SQLException {
        SchemaFilter filter = SchemaFilter.parse(ds, warnings);
        DatabaseMetaData meta = connection.getMetaData();
        JdbcScope scope = resolveScope(ds);
        Map<String, MetadataSnapshot.TableInfo> tables = new LinkedHashMap<>();
        List<MetadataSnapshot.RelationInfo> relations = new ArrayList<>();

        try (ResultSet rs = meta.getTables(scope.catalog, scope.schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String schema = safe(rs.getString("TABLE_SCHEM"));
                String table = safe(rs.getString("TABLE_NAME"));
                // MySQL 等数据库 TABLE_SCHEM 恒为 null，用 scope.effectiveSchema 兜底，避免跨库泄漏
                String effectiveSchema = StringUtils.hasText(schema) ? schema : scope.effectiveSchema;
                if (!filter.accept(effectiveSchema, table)) {
                    continue;
                }
                MetadataSnapshot.TableInfo info = new MetadataSnapshot.TableInfo();
                info.setSchemaName(effectiveSchema);
                info.setTableName(table);
                String type = safe(rs.getString("TABLE_TYPE")).toUpperCase(Locale.ROOT);
                info.setTableType(type.contains("VIEW") ? 2 : 1);
                info.setRemark(safe(rs.getString("REMARKS")));
                tables.put(effectiveSchema + "|" + table, info);
            }
        }
        warnings.add("采集范围：catalog=" + (scope.catalog == null ? "*" : scope.catalog)
                + "，schema=" + (scope.schemaPattern == null ? "*" : scope.schemaPattern)
                + "，命中表 " + tables.size() + " 张");

        for (MetadataSnapshot.TableInfo table : tables.values()) {
            collectColumns(meta, scope, table);
            collectPrimaryKeys(meta, scope, table);
            collectForeignKeys(meta, scope, table, relations, tables, warnings);
            table.setStructHash(tableHash(table));
        }
        // 注释（表/列中文名来源）：JDBC getTables/getColumns 的 REMARKS 在 MySQL 默认
        // remarksReporting=false、PG getColumns 不返回列注释，故按方言补采，失败不阻断
        enrichComments(connection, ds, tables, warnings);
        return new CollectResult(tables, relations);
    }

    /**
     * 按方言补采表 / 列注释，作为 name_zh 与 description 的来源。
     * 只填充当前仍为空的 remark，不覆盖 JDBC 已拿到的值；按本次命中的 schema 精确查询，
     * 任何异常都降级为 warning，绝不让注释采集失败阻断元数据同步。
     */
    private void enrichComments(Connection connection, AiDatasource ds,
                                Map<String, MetadataSnapshot.TableInfo> tables, List<String> warnings) {
        if (tables.isEmpty()) {
            return;
        }
        String dialect = ds.getDialect() == null ? "" : ds.getDialect().toLowerCase(Locale.ROOT);
        boolean pgFamily = dialect.startsWith("postgre") || dialect.equals("kingbase")
                || dialect.equals("opengauss");
        boolean mysql = dialect.equals("mysql");
        if (!pgFamily && !mysql) {
            warnings.add("当前方言（" + (dialect.isEmpty() ? "未识别" : dialect)
                    + "）未启用注释自动采集，表/字段中文名可在元数据管理页人工补录");
            return;
        }
        Set<String> schemas = tables.values().stream()
                .map(MetadataSnapshot.TableInfo::getSchemaName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (schemas.isEmpty()) {
            return;
        }
        Map<String, String> tableComments = new HashMap<>();
        Map<String, Map<String, String>> columnComments = new HashMap<>();
        try {
            for (String schema : schemas) {
                if (pgFamily) {
                    loadPgComments(connection, schema, tableComments, columnComments);
                } else {
                    loadMysqlComments(connection, schema, tableComments, columnComments);
                }
            }
            int filledTables = 0;
            int filledColumns = 0;
            for (Map.Entry<String, MetadataSnapshot.TableInfo> entry : tables.entrySet()) {
                MetadataSnapshot.TableInfo table = entry.getValue();
                String tc = tableComments.get(entry.getKey());
                if (!StringUtils.hasText(table.getRemark()) && StringUtils.hasText(tc)) {
                    table.setRemark(tc.trim());
                    filledTables++;
                }
                Map<String, String> cm = columnComments.get(entry.getKey());
                if (cm == null) {
                    continue;
                }
                for (MetadataSnapshot.ColumnInfo column : table.getColumns()) {
                    String cc = cm.get(column.getColumnName());
                    if (!StringUtils.hasText(column.getRemark()) && StringUtils.hasText(cc)) {
                        column.setRemark(cc.trim());
                        filledColumns++;
                    }
                }
            }
            if (filledTables + filledColumns > 0) {
                warnings.add("已从数据库注释补全中文名：表 " + filledTables + " / 字段 " + filledColumns);
            }
        } catch (SQLException ex) {
            warnings.add("数据库注释采集不可用：" + safeError(ex) + "；中文名可在元数据管理页人工补录");
        }
    }

    private void loadPgComments(Connection connection, String schema,
                                Map<String, String> tableComments,
                                Map<String, Map<String, String>> columnComments) throws SQLException {
        final String tableSql = "SELECT c.relname, obj_description(c.oid, 'pg_class') AS cmt "
                + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relkind IN ('r','p','v','m','f') "
                + "AND obj_description(c.oid, 'pg_class') IS NOT NULL";
        try (java.sql.PreparedStatement ps = connection.prepareStatement(tableSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableComments.put(schema + "|" + safe(rs.getString(1)), safe(rs.getString(2)));
                }
            }
        }
        final String columnSql = "SELECT c.relname, a.attname, col_description(c.oid, a.attnum) AS cmt "
                + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "JOIN pg_attribute a ON a.attrelid = c.oid "
                + "WHERE n.nspname = ? AND a.attnum > 0 AND NOT a.attisdropped "
                + "AND c.relkind IN ('r','p','v','m','f') "
                + "AND col_description(c.oid, a.attnum) IS NOT NULL";
        try (java.sql.PreparedStatement ps = connection.prepareStatement(columnSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = safe(rs.getString(1));
                    String columnName = safe(rs.getString(2));
                    columnComments.computeIfAbsent(schema + "|" + tableName, k -> new HashMap<>())
                            .put(columnName, safe(rs.getString(3)));
                }
            }
        }
    }

    private void loadMysqlComments(Connection connection, String schema,
                                   Map<String, String> tableComments,
                                   Map<String, Map<String, String>> columnComments) throws SQLException {
        final String tableSql = "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_COMMENT IS NOT NULL AND TABLE_COMMENT <> ''";
        try (java.sql.PreparedStatement ps = connection.prepareStatement(tableSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableComments.put(schema + "|" + safe(rs.getString(1)), safe(rs.getString(2)));
                }
            }
        }
        final String columnSql = "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND COLUMN_COMMENT IS NOT NULL AND COLUMN_COMMENT <> ''";
        try (java.sql.PreparedStatement ps = connection.prepareStatement(columnSql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = safe(rs.getString(1));
                    String columnName = safe(rs.getString(2));
                    columnComments.computeIfAbsent(schema + "|" + tableName, k -> new HashMap<>())
                            .put(columnName, safe(rs.getString(3)));
                }
            }
        }
    }

    private void collectColumns(DatabaseMetaData meta, JdbcScope scope,
                                MetadataSnapshot.TableInfo table) throws SQLException {
        try (ResultSet rs = meta.getColumns(scope.catalog, scope.schemaPattern,
                table.getTableName(), "%")) {
            while (rs.next()) {
                MetadataSnapshot.ColumnInfo column = new MetadataSnapshot.ColumnInfo();
                column.setColumnName(safe(rs.getString("COLUMN_NAME")));
                String typeName = safe(rs.getString("TYPE_NAME"));
                int size = rs.getInt("COLUMN_SIZE");
                int digits = rs.getInt("DECIMAL_DIGITS");
                if (size > 0) {
                    typeName = typeName + "(" + size + (digits > 0 ? "," + digits : "") + ")";
                }
                column.setDataType(typeName);
                column.setJdbcType(safe(rs.getString("TYPE_NAME")));
                column.setColumnSize(size);
                int nullable = rs.getInt("NULLABLE");
                column.setNullable(nullable == DatabaseMetaData.columnNullable ? 1 : 0);
                column.setDefaultValue(safe(rs.getString("COLUMN_DEF")));
                column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                column.setRemark(safe(rs.getString("REMARKS")));
                String autoIncrement = safe(rs.getString("IS_AUTOINCREMENT"));
                column.setAutoIncrement("YES".equalsIgnoreCase(autoIncrement) ? 1 : 0);
                column.setPrimaryKey(0);
                column.setStructHash(columnHash(column));
                table.getColumns().add(column);
            }
        }
        table.getColumns().sort(Comparator.comparing(MetadataSnapshot.ColumnInfo::getColumnName));
    }

    private void collectPrimaryKeys(DatabaseMetaData meta, JdbcScope scope,
                                    MetadataSnapshot.TableInfo table) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(scope.catalog, scope.schemaPattern,
                table.getTableName())) {
            while (rs.next()) {
                keys.add(safe(rs.getString("COLUMN_NAME")));
            }
        }
        table.setPrimaryKeys(keys);
        for (MetadataSnapshot.ColumnInfo column : table.getColumns()) {
            if (keys.contains(column.getColumnName())) {
                column.setPrimaryKey(1);
                column.setStructHash(columnHash(column)); // 主键信息参与列指纹
            }
        }
    }

    /** 外键 -> Join 路径候选；按 fk_name 聚合多列外键，默认不启用、未验证 */
    private void collectForeignKeys(DatabaseMetaData meta, JdbcScope scope,
                                    MetadataSnapshot.TableInfo table,
                                    List<MetadataSnapshot.RelationInfo> relations,
                                    Map<String, MetadataSnapshot.TableInfo> tables,
                                    List<String> warnings) {
        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        try (ResultSet rs = meta.getImportedKeys(scope.catalog, scope.schemaPattern,
                table.getTableName())) {
            while (rs.next()) {
                String pkSchema = safe(rs.getString("PKTABLE_SCHEM"));
                String pkTable = safe(rs.getString("PKTABLE_NAME"));
                String fkSchema = safe(rs.getString("FKTABLE_SCHEM"));
                String fkTable = safe(rs.getString("FKTABLE_NAME"));
                String pkColumn = safe(rs.getString("PKCOLUMN_NAME"));
                String fkColumn = safe(rs.getString("FKCOLUMN_NAME"));
                String fkName = safe(rs.getString("FK_NAME"));
                int seq = rs.getShort("KEY_SEQ");
                String key = pkSchema + "|" + pkTable + "|" + fkSchema + "|" + fkTable + "|" + fkName;
                groups.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new String[]{String.valueOf(seq), pkColumn, fkColumn});
            }
        } catch (SQLException ex) {
            // 部分驱动不支持 getImportedKeys（如 Hive），不阻断表列同步
            warnings.add("外键采集不可用：" + ex.getClass().getSimpleName());
            return;
        }
        for (Map.Entry<String, List<String[]>> entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            if (!tables.containsKey(parts[0] + "|" + parts[1])) {
                continue; // 被引用表不在本次同步范围内，不生成悬空 Join
            }
            List<String[]> pairs = entry.getValue();
            pairs.sort(Comparator.comparingInt(o -> Integer.parseInt(o[0])));
            List<Map<String, String>> jsonPairs = new ArrayList<>();
            for (String[] pair : pairs) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("left", pair[1]);
                item.put("right", pair[2]);
                jsonPairs.add(item);
            }
            MetadataSnapshot.RelationInfo relation = new MetadataSnapshot.RelationInfo();
            relation.setLeftTableName(parts[1]);
            relation.setRightTableName(parts[3]);
            relation.setFkName(parts[4]);
            try {
                relation.setJoinPairs(JSON.writeValueAsString(jsonPairs));
            } catch (Exception ex) {
                warnings.add("外键 " + parts[4] + " 的 join_pairs 序列化失败，已跳过");
                continue;
            }
            relations.add(relation);
        }
    }

    // ==================== 落库比对 ====================

    private void apply(AiDatasource ds, Long tenantId, CollectResult collected, boolean full,
                       Counts counts, List<String> warnings) {
        Long datasourceId = ds.getId();
        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();

        List<AiDatasourceTable> dbTables = tableMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceTable>()
                        .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                        .eq(AiDatasourceTable::getDeleted, 0));
        Map<String, AiDatasourceTable> existingTables = new HashMap<>();
        for (AiDatasourceTable row : dbTables) {
            existingTables.put(tableKey(row.getSchemaName(), row.getTableName()), row);
        }
        List<AiDatasourceColumn> dbColumns = columnMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceColumn>()
                        .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                        .eq(AiDatasourceColumn::getDeleted, 0));
        Map<String, AiDatasourceColumn> existingColumns = new HashMap<>();
        for (AiDatasourceColumn row : dbColumns) {
            existingColumns.put(columnKey(row.getSchemaName(), row.getTableName(), row.getColumnName()), row);
        }

        Set<String> seenTables = new HashSet<>();
        Set<String> seenColumns = new HashSet<>();
        Map<String, Long> tableIdByKey = new HashMap<>();
        int backfilledTables = 0;
        int backfilledColumns = 0;

        for (MetadataSnapshot.TableInfo table : collected.tables.values()) {
            String tKey = tableKey(table.getSchemaName(), table.getTableName());
            seenTables.add(tKey);
            AiDatasourceTable exists = existingTables.get(tKey);
            boolean changed = exists != null && !equalsHash(exists.getStructHash(), table.getStructHash());
            if (exists == null) {
                AiDatasourceTable row = newTable(ds, tenantId, user, now, table);
                tableMapper.insert(row);
                tableIdByKey.put(tKey, row.getId());
                counts.tableAdded++;
            } else {
                tableIdByKey.put(tKey, exists.getId());
                if (changed) {
                    AiDatasourceTable patch = new AiDatasourceTable();
                    patch.setId(exists.getId());
                    patch.setPrimaryKey(toJson(table.getPrimaryKeys()));
                    patch.setColumnCount(table.getColumns().size());
                    patch.setStructHash(table.getStructHash());
                    patch.setSyncStatus(3); // 结构已变更，等管理端确认
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    tableMapper.updateById(patch);
                    counts.tableChanged++;
                } else if (isRetired(exists.getSyncStatus())) {
                    AiDatasourceTable patch = new AiDatasourceTable();
                    patch.setId(exists.getId());
                    patch.setSyncStatus(2); // 源端重新出现
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    tableMapper.updateById(patch);
                } else if (full && Integer.valueOf(3).equals(exists.getSyncStatus())) {
                    // 全量同步时清除历史「待确认」标记，其余人工字段仍不动
                    AiDatasourceTable patch = new AiDatasourceTable();
                    patch.setId(exists.getId());
                    patch.setSyncStatus(2);
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    tableMapper.updateById(patch);
                }
            }
            Long tableId = tableIdByKey.get(tKey);

            // 历史数据补空：库里已有该表但 name_zh 为空、且本次采集到了注释时回填。
            // 只在「本来为空」时写，绝不覆盖人工录入的中文名（设计取舍第 2 条）。
            if (exists != null && !StringUtils.hasText(exists.getNameZh())
                    && StringUtils.hasText(table.getRemark())) {
                AiDatasourceTable patch = new AiDatasourceTable();
                patch.setId(exists.getId());
                patch.setNameZh(nameZhOf(table.getRemark()));
                patch.setUpdater(user);
                patch.setUpdateTime(now);
                patch.setTenantId(tenantId);
                tableMapper.updateById(patch);
                backfilledTables++;
            }

            for (MetadataSnapshot.ColumnInfo column : table.getColumns()) {
                String cKey = columnKey(table.getSchemaName(), table.getTableName(), column.getColumnName());
                seenColumns.add(cKey);
                AiDatasourceColumn columnExists = existingColumns.get(cKey);
                boolean columnChanged = columnExists != null
                        && !equalsHash(columnExists.getStructHash(), column.getStructHash());
                if (columnExists == null) {
                    columnMapper.insert(newColumn(ds, tenantId, user, now, tableId, table, column));
                    counts.columnAdded++;
                } else if (columnChanged) {
                    AiDatasourceColumn patch = new AiDatasourceColumn();
                    patch.setId(columnExists.getId());
                    patch.setDataType(column.getDataType());
                    patch.setJdbcType(column.getJdbcType());
                    patch.setColumnSize(column.getColumnSize());
                    patch.setNullable(column.getNullable());
                    patch.setPrimaryKey(column.getPrimaryKey());
                    patch.setAutoIncrement(column.getAutoIncrement());
                    patch.setDefaultValue(column.getDefaultValue());
                    patch.setOrdinalPosition(column.getOrdinalPosition());
                    patch.setStructHash(column.getStructHash());
                    patch.setSyncStatus(3);
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    columnMapper.updateById(patch);
                    counts.columnChanged++;
                } else if (isRetired(columnExists.getSyncStatus())) {
                    AiDatasourceColumn patch = new AiDatasourceColumn();
                    patch.setId(columnExists.getId());
                    patch.setSyncStatus(2);
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    columnMapper.updateById(patch);
                } else if (full && Integer.valueOf(3).equals(columnExists.getSyncStatus())) {
                    AiDatasourceColumn patch = new AiDatasourceColumn();
                    patch.setId(columnExists.getId());
                    patch.setSyncStatus(2);
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    columnMapper.updateById(patch);
                }
                // 历史数据补空：name_zh 为空且采集到注释时回填，不覆盖人工录入值
                if (columnExists != null && !StringUtils.hasText(columnExists.getNameZh())
                        && StringUtils.hasText(column.getRemark())) {
                    AiDatasourceColumn patch = new AiDatasourceColumn();
                    patch.setId(columnExists.getId());
                    patch.setNameZh(nameZhOf(column.getRemark()));
                    patch.setUpdater(user);
                    patch.setUpdateTime(now);
                    patch.setTenantId(tenantId);
                    columnMapper.updateById(patch);
                    backfilledColumns++;
                }
            }
        }

        // 源端已删除 -> 标记失效（不物理删，保留人工语义与向量可追溯性）
        for (Map.Entry<String, AiDatasourceTable> entry : existingTables.entrySet()) {
            if (seenTables.contains(entry.getKey()) || isRetired(entry.getValue().getSyncStatus())) {
                continue;
            }
            tableMapper.update(null, new LambdaUpdateWrapper<AiDatasourceTable>()
                    .eq(AiDatasourceTable::getId, entry.getValue().getId())
                    .set(AiDatasourceTable::getSyncStatus, 4)
                    .set(AiDatasourceTable::getUpdateTime, now));
            counts.tableRemoved++;
        }
        for (Map.Entry<String, AiDatasourceColumn> entry : existingColumns.entrySet()) {
            if (seenColumns.contains(entry.getKey()) || isRetired(entry.getValue().getSyncStatus())) {
                continue;
            }
            columnMapper.update(null, new LambdaUpdateWrapper<AiDatasourceColumn>()
                    .eq(AiDatasourceColumn::getId, entry.getValue().getId())
                    .set(AiDatasourceColumn::getSyncStatus, 4)
                    .set(AiDatasourceColumn::getUpdateTime, now));
            counts.columnRemoved++;
        }

        counts.relationAdded = applyRelations(ds, tenantId, user, now, collected.relations, tableIdByKey);
        if (backfilledTables > 0 || backfilledColumns > 0) {
            warnings.add("已根据数据库注释回填中文名：表 " + backfilledTables + " 张、字段 "
                    + backfilledColumns + " 个（仅填充原本为空的，未覆盖人工录入）");
        }
        if (counts.tableChanged > 0 || counts.columnChanged > 0) {
            warnings.add("检测到结构变更：表 " + counts.tableChanged + " 张、列 " + counts.columnChanged
                    + " 个已标记 sync_status=3，请人工确认业务语义后重建向量索引");
        }
    }

    /** 外键推断的 Join 路径：默认不启用、未验证，绝不覆盖人工配置 */
    private int applyRelations(AiDatasource ds, Long tenantId, String user, LocalDateTime now,
                               List<MetadataSnapshot.RelationInfo> relations,
                               Map<String, Long> tableIdByKey) {
        List<AiDatasourceTableRelation> existing = relationMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                        .eq(AiDatasourceTableRelation::getDatasourceId, ds.getId())
                        .eq(AiDatasourceTableRelation::getDeleted, 0));
        Set<String> existingKeys = new HashSet<>();
        for (AiDatasourceTableRelation row : existing) {
            existingKeys.add(row.getLeftTableId() + "|" + row.getRightTableId() + "|" + row.getJoinPairs());
        }
        int added = 0;
        for (MetadataSnapshot.RelationInfo relation : relations) {
            Long leftId = findTableId(tableIdByKey, relation.getLeftTableName());
            Long rightId = findTableId(tableIdByKey, relation.getRightTableName());
            if (leftId == null || rightId == null) {
                continue;
            }
            String key = leftId + "|" + rightId + "|" + relation.getJoinPairs();
            if (existingKeys.contains(key)) {
                continue;
            }
            existingKeys.add(key);
            AiDatasourceTableRelation row = new AiDatasourceTableRelation();
            row.setDatasourceId(ds.getId());
            row.setLeftTableId(leftId);
            row.setRightTableId(rightId);
            row.setLeftTableName(relation.getLeftTableName());
            row.setRightTableName(relation.getRightTableName());
            row.setJoinType(1);
            row.setJoinPairs(relation.getJoinPairs());
            row.setJoinCondition("");
            row.setCardinality(3); // 外键默认多对一
            row.setCost(100);
            row.setDescription("外键自动推断：" + relation.getFkName());
            row.setSource(1);
            row.setVerified(0);
            row.setEnabled(0);   // 必须人工验证后才允许给 LLM 用
            row.setStatus(0);
            row.setSort(0);
            row.setRemark("");
            row.setTenantId(tenantId);
            row.setCreator(user);
            row.setCreateTime(now);
            row.setUpdater(user);
            row.setUpdateTime(now);
            row.setDeleted(0);
            relationMapper.insert(row);
            added++;
        }
        return added;
    }

    // ==================== 可见性维护 ====================

    @Override
    public Integer updateVisibility(MetadataVisibilityReqVO reqVO) {
        AiDatasource ds = ownerValidator.requireOwned(reqVO.getDatasourceId());
        boolean hasTables = reqVO.getTableIds() != null && !reqVO.getTableIds().isEmpty();
        boolean hasColumns = reqVO.getColumnIds() != null && !reqVO.getColumnIds().isEmpty();
        if (!hasTables && !hasColumns) {
            throw exception(METADATA_VISIBILITY_IDS_EMPTY);
        }
        if (reqVO.getVisible() == null && reqVO.getQueryable() == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        Set<Long> affectedTableIds = new HashSet<>();
        Set<Long> affectedColumnIds = new HashSet<>();
        if (hasTables) {
            LambdaUpdateWrapper<AiDatasourceTable> wrapper = new LambdaUpdateWrapper<AiDatasourceTable>()
                    .eq(AiDatasourceTable::getDatasourceId, ds.getId())
                    .eq(AiDatasourceTable::getDeleted, 0)
                    .in(AiDatasourceTable::getId, reqVO.getTableIds());
            if (reqVO.getVisible() != null) {
                wrapper.set(AiDatasourceTable::getVisible, reqVO.getVisible());
            }
            if (reqVO.getQueryable() != null) {
                wrapper.set(AiDatasourceTable::getQueryable, reqVO.getQueryable());
            }
            wrapper.set(AiDatasourceTable::getUpdateTime, now);
            changed += tableMapper.update(null, wrapper);
            affectedTableIds.addAll(reqVO.getTableIds());
        }
        if (hasColumns) {
            LambdaUpdateWrapper<AiDatasourceColumn> wrapper = new LambdaUpdateWrapper<AiDatasourceColumn>()
                    .eq(AiDatasourceColumn::getDatasourceId, ds.getId())
                    .eq(AiDatasourceColumn::getDeleted, 0)
                    .in(AiDatasourceColumn::getId, reqVO.getColumnIds());
            if (reqVO.getVisible() != null) {
                wrapper.set(AiDatasourceColumn::getVisible, reqVO.getVisible());
            }
            if (reqVO.getQueryable() != null) {
                wrapper.set(AiDatasourceColumn::getQueryable, reqVO.getQueryable());
            }
            wrapper.set(AiDatasourceColumn::getUpdateTime, now);
            changed += columnMapper.update(null, wrapper);
            affectedColumnIds.addAll(reqVO.getColumnIds());
        }
        // 对应向量标记「需重建」，下次 sync 会重新判定是否下线；检索侧硬过滤不受影响。
        // 表级与列级分两次更新，避免嵌套 and/or 在不同 MP 版本里的语义差异。
        if (!affectedTableIds.isEmpty()) {
            vectorMapper.update(null, new LambdaUpdateWrapper<AiSchemaVector>()
                    .eq(AiSchemaVector::getDatasourceId, ds.getId())
                    .eq(AiSchemaVector::getDeleted, 0)
                    .in(AiSchemaVector::getTableId, affectedTableIds)
                    .set(AiSchemaVector::getSyncStatus, 2)
                    .set(AiSchemaVector::getUpdateTime, now));
        }
        if (!affectedColumnIds.isEmpty()) {
            vectorMapper.update(null, new LambdaUpdateWrapper<AiSchemaVector>()
                    .eq(AiSchemaVector::getDatasourceId, ds.getId())
                    .eq(AiSchemaVector::getDeleted, 0)
                    .in(AiSchemaVector::getColumnId, affectedColumnIds)
                    .set(AiSchemaVector::getSyncStatus, 2)
                    .set(AiSchemaVector::getUpdateTime, now));
        }
        // 脏标只是「待重算」的记号，真正生效要跑一次构建：隐藏/恢复的表要下线或重建向量行。
        if (changed > 0) {
            schemaVectorIndexSyncService.onStructureChanged(ds.getId());
        }
        return changed;
    }

    // ==================== 状态概览 ====================

    @Override
    public MetadataSyncStatusRespVO status(Long datasourceId) {
        AiDatasource ds = ownerValidator.requireOwned(datasourceId);
        MetadataSyncStatusRespVO resp = new MetadataSyncStatusRespVO();
        resp.setDatasourceId(datasourceId);
        resp.setSyncStatus(ds.getSyncStatus());
        resp.setLastSyncTime(ds.getLastSyncTime());
        resp.setStructVersion(ds.getStructVersion() == null ? 0 : ds.getStructVersion());
        resp.setTableCount(tableMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceTable>()
                .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                .eq(AiDatasourceTable::getDeleted, 0)));
        resp.setVisibleTableCount(tableMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceTable>()
                .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                .eq(AiDatasourceTable::getVisible, 1)
                .eq(AiDatasourceTable::getQueryable, 1)
                .ne(AiDatasourceTable::getSyncStatus, 4)
                .eq(AiDatasourceTable::getDeleted, 0)));
        resp.setStructChangedTableCount(tableMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceTable>()
                .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                .eq(AiDatasourceTable::getSyncStatus, 3)
                .eq(AiDatasourceTable::getDeleted, 0)));
        resp.setColumnCount(columnMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                .eq(AiDatasourceColumn::getDeleted, 0)));
        resp.setVisibleColumnCount(columnMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                .eq(AiDatasourceColumn::getVisible, 1)
                .eq(AiDatasourceColumn::getQueryable, 1)
                .ne(AiDatasourceColumn::getSyncStatus, 4)
                .eq(AiDatasourceColumn::getDeleted, 0)));
        resp.setStructChangedColumnCount(columnMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                .eq(AiDatasourceColumn::getSyncStatus, 3)
                .eq(AiDatasourceColumn::getDeleted, 0)));
        resp.setRelationCount(relationMapper.selectCount(new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                .eq(AiDatasourceTableRelation::getDatasourceId, datasourceId)
                .eq(AiDatasourceTableRelation::getDeleted, 0)));
        resp.setVerifiedRelationCount(relationMapper.selectCount(
                new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                        .eq(AiDatasourceTableRelation::getDatasourceId, datasourceId)
                        .eq(AiDatasourceTableRelation::getVerified, 1)
                        .eq(AiDatasourceTableRelation::getEnabled, 1)
                        .eq(AiDatasourceTableRelation::getDeleted, 0)));
        resp.setVectorCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getDeleted, 0)));
        resp.setVectorBuiltCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getSyncStatus, 1)
                .eq(AiSchemaVector::getDeleted, 0)));
        resp.setVectorPendingCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .ne(AiSchemaVector::getSyncStatus, 1)
                .eq(AiSchemaVector::getDeleted, 0)));
        return resp;
    }

    // ==================== 辅助 ====================

    private void writeLog(AiDatasource ds, Long tenantId, String taskNo, boolean full,
                          LocalDateTime startTime, LocalDateTime endTime, long latencyMs,
                          int status, Counts counts, int before, int after,
                          String errorMsg, List<String> warnings) {
        AiDatasourceSyncLog log = new AiDatasourceSyncLog();
        log.setDatasourceId(ds.getId());
        log.setTaskNo(taskNo);
        log.setSyncType(full ? 2 : 3);
        log.setTriggerType(1);
        log.setScope("元数据同步 schema=" + (StringUtils.hasText(ds.getSchemaName()) ? ds.getSchemaName() : "*"));
        log.setStartTime(startTime);
        log.setEndTime(endTime);
        log.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        log.setStatus(status);
        log.setTableAdded(counts.tableAdded);
        log.setTableChanged(counts.tableChanged);
        log.setTableRemoved(counts.tableRemoved);
        log.setColumnAdded(counts.columnAdded);
        log.setColumnChanged(counts.columnChanged);
        log.setColumnRemoved(counts.columnRemoved);
        log.setRelationAdded(counts.relationAdded);
        log.setEnumAdded(0);
        List<String> details = new ArrayList<>(warnings);
        details.add("新增表 " + counts.tableAdded + " / 变更表 " + counts.tableChanged
                + " / 新增列 " + counts.columnAdded + " / 变更列 " + counts.columnChanged
                + " / 外键路径 " + counts.relationAdded);
        log.setChangeDetail(truncate(String.join("；", details), MAX_CHANGE_DETAIL));
        log.setStructVersionBefore(before);
        log.setStructVersionAfter(after);
        log.setErrorCode("");
        log.setErrorMsg(truncate(errorMsg, MAX_CHANGE_DETAIL));
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        log.setOperatorId(userId == null ? 0L : userId);
        log.setOperatorName(userId == null ? "" : userId.toString());
        log.setTenantId(tenantId);
        syncLogService.createLog(log);
    }

    private AiDatasourceTable newTable(AiDatasource ds, Long tenantId, String user, LocalDateTime now,
                                       MetadataSnapshot.TableInfo table) {
        AiDatasourceTable row = new AiDatasourceTable();
        row.setDatasourceId(ds.getId());
        row.setSchemaName(table.getSchemaName());
        row.setTableName(table.getTableName());
        row.setTableType(table.getTableType());
        row.setNameZh(nameZhOf(table.getRemark()));
        row.setDescription(truncate(table.getRemark(), 1000));
        row.setTableRole(0);
        row.setPrimaryKey(toJson(table.getPrimaryKeys()));
        row.setDefaultTimeColumn("");
        row.setRowCountEstimate(0L);
        row.setColumnCount(table.getColumns().size());
        row.setSampleRows("");
        row.setVisible(1);
        row.setQueryable(1);
        row.setSensitiveLevel(0);
        row.setSyncStatus(2);
        row.setStructHash(table.getStructHash());
        row.setLastSyncTime(now);
        row.setSort(0);
        row.setRemark("");
        row.setTenantId(tenantId);
        row.setCreator(user);
        row.setCreateTime(now);
        row.setUpdater(user);
        row.setUpdateTime(now);
        row.setDeleted(0);
        return row;
    }

    private AiDatasourceColumn newColumn(AiDatasource ds, Long tenantId, String user, LocalDateTime now,
                                         Long tableId, MetadataSnapshot.TableInfo table,
                                         MetadataSnapshot.ColumnInfo column) {
        AiDatasourceColumn row = new AiDatasourceColumn();
        row.setDatasourceId(ds.getId());
        row.setTableId(tableId);
        row.setSchemaName(table.getSchemaName());
        row.setTableName(table.getTableName());
        row.setColumnName(column.getColumnName());
        row.setNameZh(nameZhOf(column.getRemark()));
        row.setDescription(truncate(column.getRemark(), 1000));
        row.setDataType(column.getDataType());
        row.setJdbcType(column.getJdbcType());
        row.setColumnSize(column.getColumnSize());
        row.setNullable(column.getNullable());
        row.setPrimaryKey(column.getPrimaryKey());
        row.setAutoIncrement(column.getAutoIncrement());
        row.setDefaultValue(column.getDefaultValue());
        row.setOrdinalPosition(column.getOrdinalPosition());
        row.setSemanticRole(0);
        row.setAggregation("");
        row.setUnit("");
        row.setTimeFormat("");
        row.setIsDefaultTime(0);
        row.setMetricDef("");
        row.setSynonyms("");
        row.setSampleValues("");
        row.setDistinctCountEstimate(0L);
        row.setEnumEnabled(0);
        row.setSensitiveType(0);
        row.setMaskRule("");
        row.setVisible(1);
        row.setQueryable(1);
        row.setFilterable(0);
        row.setSyncStatus(2);
        row.setStructHash(column.getStructHash());
        row.setSort(0);
        row.setRemark("");
        row.setTenantId(tenantId);
        row.setCreator(user);
        row.setCreateTime(now);
        row.setUpdater(user);
        row.setUpdateTime(now);
        row.setDeleted(0);
        return row;
    }

    private static String tableHash(MetadataSnapshot.TableInfo table) {
        String payload = table.getColumns().stream()
                .sorted(Comparator.comparing(MetadataSnapshot.ColumnInfo::getColumnName))
                .map(c -> c.getColumnName() + ":" + c.getDataType() + ":" + c.getNullable()
                        + ":" + c.getPrimaryKey())
                .collect(Collectors.joining("|"));
        return sha256(payload);
    }

    private static String columnHash(MetadataSnapshot.ColumnInfo column) {
        return sha256(String.join("|",
                safe(column.getDataType()), safe(column.getJdbcType()),
                String.valueOf(column.getColumnSize()), String.valueOf(column.getNullable()),
                safe(column.getDefaultValue()), String.valueOf(column.getAutoIncrement()),
                String.valueOf(column.getPrimaryKey()), String.valueOf(column.getOrdinalPosition())));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /** 与 P0 的 AiDatasourceServiceImpl#sanitizeError 同口径：剔除数据库本地化乱码 */
    private static String safeError(Exception ex) {
        String raw = ex.getClass().getSimpleName() + ": "
                + (ex.getMessage() == null ? "" : ex.getMessage());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ascii = c >= 0x20 && c <= 0x7E;
            boolean cjk = c >= 0x4E00 && c <= 0x9FFF;
            boolean punctuation = c == 0xFF0C || c == 0x3002 || c == 0xFF1A
                    || c == 0xFF08 || c == 0xFF09 || c == 0x3001;
            if (!ascii && !cjk && !punctuation) {
                return "元数据同步失败（" + ex.getClass().getSimpleName()
                        + "；数据库返回的本地化消息含非 UTF-8 字符，已省略原文）";
            }
        }
        return truncate(raw, 1000);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max);
    }

    /**
     * 数据库注释 -> 中文名：取第一行（注释常有多行说明），压平空白，截断到 name_zh 的 200 字符上限。
     * MySQL 表注释在 8.0 以下可能附带 "; COMMENT=..." 之类的后缀，这里只保留首行足以得到干净的中文名。
     */
    private static String nameZhOf(String remark) {
        if (!StringUtils.hasText(remark)) {
            return "";
        }
        String firstLine = remark;
        int lf = firstLine.indexOf('\n');
        if (lf >= 0) {
            firstLine = firstLine.substring(0, lf);
        }
        int cr = firstLine.indexOf('\r');
        if (cr >= 0) {
            firstLine = firstLine.substring(0, cr);
        }
        return truncate(firstLine.trim(), 200);
    }

    private static String toJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

    private static String tableKey(String schema, String table) {
        return safe(schema) + "|" + safe(table);
    }

    private static String columnKey(String schema, String table, String column) {
        return safe(schema) + "|" + safe(table) + "|" + safe(column);
    }

    private static Long findTableId(Map<String, Long> tableIdByKey, String tableName) {
        for (Map.Entry<String, Long> entry : tableIdByKey.entrySet()) {
            if (entry.getKey().endsWith("|" + tableName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean equalsHash(String a, String b) {
        return safe(a).equals(safe(b));
    }

    private static boolean isRetired(Integer syncStatus) {
        return Integer.valueOf(4).equals(syncStatus);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    // ==================== 内部类 ====================

    private static class Counts {
        private int tableAdded;
        private int tableChanged;
        private int tableRemoved;
        private int columnAdded;
        private int columnChanged;
        private int columnRemoved;
        private int relationAdded;

        private boolean hasChanges() {
            return tableAdded + tableChanged + tableRemoved
                    + columnAdded + columnChanged + columnRemoved + relationAdded > 0;
        }
    }

    private static class CollectResult {
        private final Map<String, MetadataSnapshot.TableInfo> tables;
        private final List<MetadataSnapshot.RelationInfo> relations;

        private CollectResult(Map<String, MetadataSnapshot.TableInfo> tables,
                              List<MetadataSnapshot.RelationInfo> relations) {
            this.tables = tables;
            this.relations = relations;
        }
    }

    /** 按方言解析后的 JDBC 元数据调用参数；effectiveSchema 用于 MySQL 等 schema 恒为 null 的兜底 */
    private static class JdbcScope {
        private String catalog;
        private String schemaPattern;
        private String effectiveSchema;
    }

    /** allowed_schemas / table_filter 的过滤器；配置非法时直接拒绝同步，避免误同步全库 */
    private static class SchemaFilter {
        private final Set<String> allowedSchemas;
        private final List<Pattern> includes;
        private final List<Pattern> excludes;

        private SchemaFilter(Set<String> allowedSchemas, List<Pattern> includes, List<Pattern> excludes) {
            this.allowedSchemas = allowedSchemas;
            this.includes = includes;
            this.excludes = excludes;
        }

        private static SchemaFilter parse(AiDatasource ds, List<String> warnings) {
            Set<String> allowed = new LinkedHashSet<>();
            if (StringUtils.hasText(ds.getAllowedSchemas())) {
                try {
                    JsonNode node = JSON.readTree(ds.getAllowedSchemas());
                    if (!node.isArray()) {
                        throw new IllegalArgumentException("不是 JSON 数组");
                    }
                    for (JsonNode item : node) {
                        allowed.add(item.asText());
                    }
                } catch (Exception ex) {
                    throw new IllegalArgumentException("allowed_schemas 必须是合法 JSON 数组，例如 [\"public\"]");
                }
            }
            List<Pattern> includes = new ArrayList<>();
            List<Pattern> excludes = new ArrayList<>();
            if (StringUtils.hasText(ds.getTableFilter())) {
                try {
                    JsonNode node = JSON.readTree(ds.getTableFilter());
                    compile(node.get("include"), includes);
                    compile(node.get("exclude"), excludes);
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "table_filter 必须是 {\"include\":[...],\"exclude\":[...]} 形式的合法 JSON");
                }
            }
            if (!allowed.isEmpty()) {
                warnings.add("schema 白名单：" + allowed);
            }
            return new SchemaFilter(allowed, includes, excludes);
        }

        private static void compile(JsonNode node, List<Pattern> target) {
            if (node == null || !node.isArray()) {
                return;
            }
            for (JsonNode item : node) {
                target.add(Pattern.compile(item.asText()));
            }
        }

        private boolean accept(String schema, String table) {
            String s = safe(schema);
            if (SYSTEM_SCHEMAS.contains(s.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (!allowedSchemas.isEmpty() && !allowedSchemas.contains(s)) {
                return false;
            }
            String lowerTable = safe(table).toLowerCase(Locale.ROOT);
            for (String prefix : SYSTEM_TABLE_PREFIXES) {
                if (lowerTable.startsWith(prefix)) {
                    return false;
                }
            }
            for (Pattern pattern : excludes) {
                if (pattern.matcher(safe(table)).find()) {
                    return false;
                }
            }
            if (!includes.isEmpty()) {
                for (Pattern pattern : includes) {
                    if (pattern.matcher(safe(table)).find()) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }

}
