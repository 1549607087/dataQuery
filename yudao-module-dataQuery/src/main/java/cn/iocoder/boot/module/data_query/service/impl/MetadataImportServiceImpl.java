package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceTestRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataImportReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataImportRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataPreviewRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorBuildRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.framework.importmeta.MetadataImportProperties;
import cn.iocoder.boot.module.data_query.service.AiDatasourceService;
import cn.iocoder.boot.module.data_query.service.MetadataImportService;
import cn.iocoder.boot.module.data_query.service.MetadataSyncService;
import cn.iocoder.boot.module.data_query.service.SchemaVectorBuildService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_CODE_CONFLICT;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_CONNECTION_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_DRIVER_UNKNOWN;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_MODE_INVALID;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_NEW_MISSING_FIELDS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_SYNC_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.METADATA_IMPORT_TABLE_LIMIT;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 表结构一键导入实现
 *
 * 关键取舍：把「扫描」放在「创建数据源」之前。连不上库、扫不到表、超表数上限时
 * 直接返回，一条数据都不写，用户改完参数重提即可；不会留下一个连不通的空壳数据源。
 */
@Service
public class MetadataImportServiceImpl implements MetadataImportService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private MetadataImportProperties properties;

    @Resource
    private MetadataSyncService metadataSyncService;

    @Resource
    private AiDatasourceService datasourceService;

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;

    @Resource
    private SchemaVectorBuildService vectorBuildService;

    @Override
    public MetadataPreviewRespVO preview(MetadataImportReqVO reqVO) {
        if (reqVO == null) {
            throw exception(METADATA_IMPORT_MODE_INVALID);
        }
        AiDatasource probe = resolveProbe(reqVO);
        return doPreview(reqVO, probe);
    }

    @Override
    public MetadataImportRespVO importMetadata(MetadataImportReqVO reqVO) {
        if (reqVO == null) {
            throw exception(METADATA_IMPORT_MODE_INVALID);
        }
        long begin = System.currentTimeMillis();
        MetadataImportRespVO resp = new MetadataImportRespVO();
        List<MetadataImportRespVO.Step> steps = resp.getSteps();
        List<String> warnings = resp.getWarnings();
        resp.setSuccess(false);
        resp.setDryRun(Boolean.TRUE.equals(reqVO.getDryRun()));
        resp.setDatasourceCreated(false);
        resp.setDatasourceReused(false);

        boolean existingMode = reqVO.getDatasourceId() != null;
        AiDatasource probe = resolveProbe(reqVO);

        // 编码冲突在连目标库之前判定：错误语义更明确（不是「扫不到表」），
        // 也避免为一次注定失败的提交白开一条 JDBC 连接。dryRun 只做预览，不做冲突判定。
        AiDatasource existing = null;
        if (!existingMode && !Boolean.TRUE.equals(resp.getDryRun())) {
            existing = findByCode(reqVO.getCode());
            if (existing != null && (Boolean.FALSE.equals(reqVO.getReuseExisting())
                    || !sameConnection(existing, probe))) {
                throw exception(METADATA_IMPORT_CODE_CONFLICT);
            }
        }

        // ---- 1. 只读扫描：与正式同步同一套采集 / 过滤规则，零落库副作用 ----
        long t = System.currentTimeMillis();
        MetadataPreviewRespVO preview = doPreview(reqVO, probe);
        resp.setPreview(preview);
        warnings.addAll(preview.getWarnings());
        if (!Boolean.TRUE.equals(preview.getSuccess())) {
            steps.add(step("PREVIEW", "FAILED", "无法读取目标库结构", System.currentTimeMillis() - t));
            return fail(resp, begin, "PREVIEW", METADATA_IMPORT_CONNECTION_FAILED.getCode(),
                    preview.getErrorMsg());
        }
        steps.add(step("PREVIEW", "SUCCESS", "扫描到 " + preview.getTableCount() + " 张表 / "
                + preview.getColumnCount() + " 个字段", System.currentTimeMillis() - t));

        Integer maxTables = properties.getMaxTables();
        if (preview.getTableCount() != null && maxTables != null && preview.getTableCount() > maxTables) {
            throw exception(METADATA_IMPORT_TABLE_LIMIT);
        }
        if (Boolean.TRUE.equals(resp.getDryRun())) {
            resp.setSuccess(true);
            resp.setStage("PREVIEW");
            resp.setSummary("预览完成：将导入 " + preview.getTableCount() + " 张表 / "
                    + preview.getColumnCount() + " 个字段；未创建数据源、未写入任何表结构。");
            resp.setLatencyMs(System.currentTimeMillis() - begin);
            return resp;
        }
        if (preview.getTableCount() == null || preview.getTableCount() == 0) {
            return fail(resp, begin, "PREVIEW", METADATA_IMPORT_SYNC_FAILED.getCode(),
                    "指定范围内没有可导入的表：请检查 schemaName / allowedSchemas / includeTables 配置");
        }

        // ---- 2. 创建或复用数据源（按 code 幂等） ----
        t = System.currentTimeMillis();
        Long datasourceId;
        if (existingMode) {
            datasourceId = reqVO.getDatasourceId();
            resp.setDatasourceReused(true);
            steps.add(step("DATASOURCE", "SUCCESS", "复用已有数据源 #" + datasourceId,
                    System.currentTimeMillis() - t));
        } else {
            // 二次兜底：并发提交同一 code 时，前置检查之后可能已有别的事务插入
            if (existing == null) {
                existing = findByCode(reqVO.getCode());
            }
            if (existing != null) {
                boolean reuse = !Boolean.FALSE.equals(reqVO.getReuseExisting());
                if (!reuse || !sameConnection(existing, probe)) {
                    throw exception(METADATA_IMPORT_CODE_CONFLICT);
                }
                datasourceId = existing.getId();
                resp.setDatasourceReused(true);
                warnings.add("数据源编码已存在，复用 #" + datasourceId
                        + "；本次提交的凭证未覆盖已有配置，如需改密码请用数据源编辑页。");
                steps.add(step("DATASOURCE", "SUCCESS", "幂等复用数据源 #" + datasourceId,
                        System.currentTimeMillis() - t));
            } else {
                datasourceId = datasourceService.createDatasource(buildEntity(reqVO));
                resp.setDatasourceCreated(true);
                steps.add(step("DATASOURCE", "SUCCESS", "新建数据源 #" + datasourceId,
                        System.currentTimeMillis() - t));
            }
        }
        resp.setDatasourceId(datasourceId);
        // 同步沿用本次预览的同一份配置（含扫描范围），保证「预览所见 = 落库所得」；
        // 若回读数据库里的配置，复用模式或改了范围的场景会和预览结果对不上。
        probe.setId(datasourceId);

        // ---- 3. 连通性检测（复用 P0 实现，会回写健康状态与同步日志） ----
        t = System.currentTimeMillis();
        AiDatasourceTestRespVO connection;
        try {
            connection = datasourceService.testConnection(datasourceId);
        } catch (Exception ex) {
            steps.add(step("CONNECT", "FAILED", "连通性检测异常", System.currentTimeMillis() - t));
            return fail(resp, begin, "CONNECT", METADATA_IMPORT_CONNECTION_FAILED.getCode(),
                    scrub(ex.getMessage(), reqVO));
        }
        resp.setConnection(connection);
        if (connection == null || !Boolean.TRUE.equals(connection.getSuccess())) {
            steps.add(step("CONNECT", "FAILED", "目标库连不通", System.currentTimeMillis() - t));
            return fail(resp, begin, "CONNECT", METADATA_IMPORT_CONNECTION_FAILED.getCode(),
                    scrub(connection == null ? "" : connection.getMessage(), reqVO));
        }
        steps.add(step("CONNECT", "SUCCESS", "连通性检测通过", System.currentTimeMillis() - t));

        // ---- 4. 全量元数据同步（P1 实现；新表 / 新列默认 visible=1 / queryable=1） ----
        t = System.currentTimeMillis();
        MetadataSyncRespVO sync;
        try {
            sync = metadataSyncService.sync(probe, true);
        } catch (Exception ex) {
            steps.add(step("SYNC", "FAILED", "元数据同步异常", System.currentTimeMillis() - t));
            return fail(resp, begin, "SYNC", METADATA_IMPORT_SYNC_FAILED.getCode(),
                    scrub(ex.getMessage(), reqVO));
        }
        resp.setSync(sync);
        if (sync.getWarnings() != null) {
            for (String warning : sync.getWarnings()) {
                warnings.add(scrub(warning, reqVO));
            }
        }
        if (!Boolean.TRUE.equals(sync.getSuccess())) {
            steps.add(step("SYNC", "FAILED", "元数据同步未成功", System.currentTimeMillis() - t));
            return fail(resp, begin, "SYNC", METADATA_IMPORT_SYNC_FAILED.getCode(),
                    scrub(sync.getErrorMsg(), reqVO));
        }
        steps.add(step("SYNC", "SUCCESS", "新增表 " + sync.getTableAdded() + " / 新增字段 "
                + sync.getColumnAdded() + " / Join 候选 " + sync.getRelationAdded(),
                System.currentTimeMillis() - t));

        // ---- 5. 向量索引（非阻断：失败或未接线只告警，元数据已可直接用于关键词召回） ----
        boolean buildVector = reqVO.getBuildVector() == null
                ? Boolean.TRUE.equals(properties.getBuildVectorByDefault())
                : reqVO.getBuildVector();
        if (!buildVector) {
            steps.add(step("VECTOR", "SKIPPED", "按请求跳过向量索引构建", 0L));
        } else {
            t = System.currentTimeMillis();
            try {
                SchemaVectorBuildRespVO vector = vectorBuildService.build(datasourceId, false);
                resp.setVector(vector);
                if (vector.getWarnings() != null) {
                    for (String warning : vector.getWarnings()) {
                        warnings.add(scrub(warning, reqVO));
                    }
                }
                if (!Boolean.TRUE.equals(vector.getEmbeddingAvailable())) {
                    steps.add(step("VECTOR", "WARN", "Document 已入库，本租户 Embedding 未接线（待补齐，不阻断导入）",
                            System.currentTimeMillis() - t));
                } else {
                    steps.add(step("VECTOR", "SUCCESS", "向量 " + vector.getEmbedded() + " 条已生成",
                            System.currentTimeMillis() - t));
                }
            } catch (Exception ex) {
                warnings.add("向量索引未构建（非阻断，可稍后重建）：" + scrub(ex.getMessage(), reqVO));
                steps.add(step("VECTOR", "WARN", "构建异常，元数据导入不受影响",
                        System.currentTimeMillis() - t));
            }
        }

        if (!StringUtils.hasText(reqVO.getReadonlyUrl()) && !existingMode) {
            warnings.add("未配置独立只读账号：问数执行会降级复用管理连接（S7 仍强制 setReadOnly / LIMIT / 超时）。"
                    + "生产上线前请在数据源编辑页补 readonlyUrl 与只读账号。");
        }

        resp.setSuccess(true);
        resp.setStage("DONE");
        resp.setSummary(buildSummary(resp));
        resp.setLatencyMs(System.currentTimeMillis() - begin);
        return resp;
    }

    // ==================== 辅助 ====================

    /** 已有数据源走归属校验，并把本次请求的扫描范围覆盖到副本上；新建模式构造一个尚未落库的临时连接配置 */
    private AiDatasource resolveProbe(MetadataImportReqVO reqVO) {
        if (reqVO.getDatasourceId() != null) {
            // 复用模式：表单里的扫描范围只对本次导入生效，不能改到库里的数据源配置，
            // 因此复制一份连接配置再覆盖 allowed_schemas / table_filter（原实现直接返回实体，范围被丢弃）
            return applyScope(ownerValidator.requireOwned(reqVO.getDatasourceId()), reqVO);
        }
        // 完全空的表单是「没选模式」，与「选了新建但填漏了」区分开，便于前端给出准确引导
        if (!StringUtils.hasText(reqVO.getName()) && !StringUtils.hasText(reqVO.getCode())
                && !StringUtils.hasText(reqVO.getJdbcUrl())) {
            throw exception(METADATA_IMPORT_MODE_INVALID);
        }
        if (!StringUtils.hasText(reqVO.getName()) || !StringUtils.hasText(reqVO.getCode())
                || !StringUtils.hasText(reqVO.getJdbcUrl())) {
            throw exception(METADATA_IMPORT_NEW_MISSING_FIELDS);
        }
        return buildProbe(reqVO);
    }

    private MetadataPreviewRespVO doPreview(MetadataImportReqVO reqVO, AiDatasource probe) {
        MetadataPreviewRespVO pv = metadataSyncService.preview(probe,
                properties.getMaxTables(), properties.getMaxPreviewTables(),
                properties.getMaxColumnsPerPreviewTable());
        if (reqVO.getDatasourceId() != null) {
            pv.setDatasourceId(reqVO.getDatasourceId());
        }
        pv.setErrorMsg(scrub(pv.getErrorMsg(), reqVO));
        List<String> clean = new ArrayList<>();
        for (String warning : pv.getWarnings()) {
            clean.add(scrub(warning, reqVO));
        }
        pv.setWarnings(clean);
        return pv;
    }

    private AiDatasource buildProbe(MetadataImportReqVO reqVO) {
        int dsType = reqVO.getDsType() == null ? properties.getDefaultDsType() : reqVO.getDsType();
        String[] preset = preset(dsType, reqVO);
        AiDatasource ds = new AiDatasource();
        ds.setName(trim(reqVO.getName()));
        ds.setCode(trim(reqVO.getCode()));
        ds.setDsType(dsType);
        ds.setDialect(StringUtils.hasText(reqVO.getDialect()) ? reqVO.getDialect().trim() : preset[1]);
        ds.setDriverClass(StringUtils.hasText(reqVO.getDriverClass()) ? reqVO.getDriverClass().trim() : preset[0]);
        ds.setJdbcUrl(reqVO.getJdbcUrl().trim());
        ds.setUsername(trim(reqVO.getUsername()));
        ds.setPassword(trim(reqVO.getPassword()));
        ds.setSchemaName(resolveSchemaName(reqVO, preset[2]));
        ds.setCatalogName(trim(reqVO.getCatalogName()));
        // 预览与连通性检测一律走管理连接；正式问数执行仍由 S7 按 readonly_enabled 决定通道
        ds.setReadonlyEnabled(0);
        ds.setAllowedSchemas(toJsonArray(reqVO.getAllowedSchemas()));
        ds.setTableFilter(toTableFilter(reqVO));
        ds.setStatus(0);
        ds.setMaxRows(reqVO.getMaxRows() != null ? reqVO.getMaxRows() : properties.getDefaultMaxRows());
        ds.setSort(0);
        return ds;
    }

    private AiDatasource buildEntity(MetadataImportReqVO reqVO) {
        AiDatasource ds = buildProbe(reqVO);
        ds.setReadonlyUrl(trim(reqVO.getReadonlyUrl()));
        ds.setReadonlyUsername(trim(reqVO.getReadonlyUsername()));
        ds.setReadonlyPassword(trim(reqVO.getReadonlyPassword()));
        ds.setReadonlyEnabled(StringUtils.hasText(reqVO.getReadonlyUrl()) ? 1 : 0);
        ds.setRemark("由「表结构一键导入」创建");
        return ds;
    }

    /** {driverClass, dialect, defaultSchema}；未登记的数据库类型必须显式给 driverClass */
    private static String[] preset(int dsType, MetadataImportReqVO reqVO) {
        switch (dsType) {
            case 1:
                return new String[]{"com.mysql.cj.jdbc.Driver", "mysql", ""};
            case 2:
                return new String[]{"org.postgresql.Driver", "postgresql", "public"};
            case 3:
                return new String[]{"oracle.jdbc.OracleDriver", "oracle", ""};
            case 4:
                return new String[]{"com.microsoft.sqlserver.jdbc.SQLServerDriver", "sqlserver", ""};
            case 5:
                return new String[]{"dm.jdbc.driver.DmDriver", "dm", ""};
            case 6:
                return new String[]{"com.kingbase8.Driver", "postgresql", "public"};
            case 7:
                return new String[]{"org.opengauss.Driver", "postgresql", "public"};
            case 8:
                return new String[]{"com.clickhouse.jdbc.ClickHouseDriver", "clickhouse", ""};
            case 9:
                return new String[]{"org.apache.hive.jdbc.HiveDriver", "hive", ""};
            default:
                if (!StringUtils.hasText(reqVO.getDriverClass())) {
                    throw exception(METADATA_IMPORT_DRIVER_UNKNOWN);
                }
                return new String[]{reqVO.getDriverClass().trim(), null, ""};
        }
    }

    private static String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String toTableFilter(MetadataImportReqVO reqVO) {
        boolean hasInclude = reqVO.getIncludeTables() != null && !reqVO.getIncludeTables().isEmpty();
        boolean hasExclude = reqVO.getExcludeTables() != null && !reqVO.getExcludeTables().isEmpty();
        if (!hasInclude && !hasExclude) {
            return null;
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("include", hasInclude ? reqVO.getIncludeTables() : List.of());
        filter.put("exclude", hasExclude ? reqVO.getExcludeTables() : List.of());
        try {
            return JSON.writeValueAsString(filter);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * schema 的优先级：显式填写 > schema 白名单 > 数据库类型默认值。
     *
     * 白名单只有一项时直接把它当作 JDBC 的 schemaPattern（等价且更快）；填了多项时必须让
     * schemaPattern 为空，否则 JDBC 只会扫默认 schema，白名单里的其它 schema 永远命中不到——
     * 表现就是「白名单填了不起作用」。
     */
    private static String resolveSchemaName(MetadataImportReqVO reqVO, String presetSchema) {
        if (StringUtils.hasText(reqVO.getSchemaName())) {
            return reqVO.getSchemaName().trim();
        }
        List<String> allowed = reqVO.getAllowedSchemas();
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.size() == 1 ? allowed.get(0).trim() : null;
        }
        return presetSchema;
    }

    /**
     * 复用已有数据源时，把本次请求的扫描范围覆盖到「连接配置副本」上。
     *
     * 只影响本次导入的预览与同步，不回写数据源配置；未填任何范围时直接返回原实体，保持既有行为。
     */
    private static AiDatasource applyScope(AiDatasource source, MetadataImportReqVO reqVO) {
        boolean hasSchemas = reqVO.getAllowedSchemas() != null && !reqVO.getAllowedSchemas().isEmpty();
        boolean hasInclude = reqVO.getIncludeTables() != null && !reqVO.getIncludeTables().isEmpty();
        boolean hasExclude = reqVO.getExcludeTables() != null && !reqVO.getExcludeTables().isEmpty();
        if (!hasSchemas && !hasInclude && !hasExclude) {
            return source;
        }
        AiDatasource copy = new AiDatasource();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setCode(source.getCode());
        copy.setDsType(source.getDsType());
        copy.setDialect(source.getDialect());
        copy.setDriverClass(source.getDriverClass());
        copy.setJdbcUrl(source.getJdbcUrl());
        copy.setUsername(source.getUsername());
        copy.setPassword(source.getPassword());
        copy.setReadonlyEnabled(source.getReadonlyEnabled());
        copy.setReadonlyUrl(source.getReadonlyUrl());
        copy.setReadonlyUsername(source.getReadonlyUsername());
        copy.setReadonlyPassword(source.getReadonlyPassword());
        copy.setCatalogName(source.getCatalogName());
        copy.setMaxRows(source.getMaxRows());
        copy.setSchemaName(hasSchemas ? resolveSchemaName(reqVO, source.getSchemaName()) : source.getSchemaName());
        copy.setAllowedSchemas(hasSchemas ? toJsonArray(reqVO.getAllowedSchemas()) : source.getAllowedSchemas());
        copy.setTableFilter(mergeTableFilter(source.getTableFilter(), reqVO));
        return copy;
    }

    /** 请求里的 include / exclude 覆盖数据源自带规则，未填的那一侧沿用原配置 */
    private static String mergeTableFilter(String baseFilter, MetadataImportReqVO reqVO) {
        boolean hasInclude = reqVO.getIncludeTables() != null && !reqVO.getIncludeTables().isEmpty();
        boolean hasExclude = reqVO.getExcludeTables() != null && !reqVO.getExcludeTables().isEmpty();
        if (!hasInclude && !hasExclude) {
            return baseFilter;
        }
        List<String> includes = hasInclude ? reqVO.getIncludeTables() : readPatterns(baseFilter, "include");
        List<String> excludes = hasExclude ? reqVO.getExcludeTables() : readPatterns(baseFilter, "exclude");
        if (includes.isEmpty() && excludes.isEmpty()) {
            return null;
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("include", includes);
        filter.put("exclude", excludes);
        try {
            return JSON.writeValueAsString(filter);
        } catch (Exception ex) {
            return baseFilter;
        }
    }

    private static List<String> readPatterns(String filterJson, String field) {
        if (!StringUtils.hasText(filterJson)) {
            return List.of();
        }
        try {
            JsonNode node = JSON.readTree(filterJson).get(field);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                values.add(item.asText());
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** 按租户内唯一编码查已有数据源；TenantDatabaseInterceptor 保证只命中本租户 */
    private AiDatasource findByCode(String code) {
        List<AiDatasource> found = datasourceService.list(new LambdaQueryWrapperX<AiDatasource>()
                .eq(AiDatasource::getCode, trim(code))
                .eq(AiDatasource::getDeleted, 0)
                .last("LIMIT 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    private static boolean sameConnection(AiDatasource exists, AiDatasource probe) {
        return trim(exists.getJdbcUrl()).equals(trim(probe.getJdbcUrl()))
                && trim(exists.getUsername()).equals(trim(probe.getUsername()))
                && trim(exists.getSchemaName()).equals(trim(probe.getSchemaName()));
    }

    /** 连接串、URL 里的 password 参数与请求里的明文密码一律不进入响应与日志 */
    private static String scrub(String text, MetadataImportReqVO reqVO) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String value = text.replaceAll("(?i)jdbc:[^\\s\"'<>)]+", "jdbc:***")
                .replaceAll("(?i)(password|pwd|pass)=([^&\\s\"'<>]+)", "$1=***");
        if (reqVO != null) {
            if (StringUtils.hasText(reqVO.getPassword())) {
                value = value.replace(reqVO.getPassword(), "***");
            }
            if (StringUtils.hasText(reqVO.getReadonlyPassword())) {
                value = value.replace(reqVO.getReadonlyPassword(), "***");
            }
            if (StringUtils.hasText(reqVO.getJdbcUrl())) {
                value = value.replace(reqVO.getJdbcUrl(), "jdbc:***");
            }
        }
        return value.length() > 600 ? value.substring(0, 600) : value;
    }

    private static MetadataImportRespVO.Step step(String name, String status, String detail, long latencyMs) {
        MetadataImportRespVO.Step item = new MetadataImportRespVO.Step();
        item.setName(name);
        item.setStatus(status);
        item.setDetail(detail);
        item.setLatencyMs(latencyMs);
        return item;
    }

    private static MetadataImportRespVO fail(MetadataImportRespVO resp, long begin, String stage,
                                             Integer errorCode, String message) {
        resp.setSuccess(false);
        resp.setStage(stage);
        resp.setErrorCode(errorCode == null ? null : errorCode.longValue());
        resp.setErrorMsg(message);
        resp.setSummary("一键导入在「" + stage + "」阶段中断：" + message);
        resp.setLatencyMs(System.currentTimeMillis() - begin);
        return resp;
    }

    private static String buildSummary(MetadataImportRespVO resp) {
        MetadataPreviewRespVO pv = resp.getPreview();
        int tables = pv == null || pv.getTableCount() == null ? 0 : pv.getTableCount();
        int columns = pv == null || pv.getColumnCount() == null ? 0 : pv.getColumnCount();
        SchemaVectorBuildRespVO vector = resp.getVector();
        String vectorText;
        if (vector == null) {
            vectorText = "向量索引未构建";
        } else if (!Boolean.TRUE.equals(vector.getEmbeddingAvailable())) {
            vectorText = "Document 已入库、本租户 Embedding 未接线（待补齐）";
        } else {
            vectorText = vector.getEmbedded() + " 条向量已生成";
        }
        return "一键导入完成：数据源 #" + resp.getDatasourceId()
                + (Boolean.TRUE.equals(resp.getDatasourceCreated()) ? "（新建）" : "（复用）")
                + "，" + tables + " 张表 / " + columns + " 个字段，" + vectorText + "。";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

}
