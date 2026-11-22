package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.AiSchemaVectorPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.AiSchemaVectorRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorBuildRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorStatusRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumnEnum;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceSyncLog;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.entity.AiSchemaVector;
import cn.iocoder.boot.module.data_query.framework.datasource.AiDatasourceOwnerValidator;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaDocument;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaEmbeddingProvider;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceColumnEnumMapper;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceColumnMapper;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceTableMapper;
import cn.iocoder.boot.module.data_query.mapper.AiSchemaVectorMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceSyncLogService;
import cn.iocoder.boot.module.data_query.service.AiSchemaVectorService;
import cn.iocoder.boot.module.data_query.service.SchemaVectorBuildService;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_VECTOR_DIM_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * Schema 向量索引写入侧实现（P1）
 *
 * 安全与口径约束：
 * 1. 只取 visible = 1 AND queryable = 1 AND sync_status <> 4 的表与列；
 *    mask_rule = 'forbid' 的列整列不生成 Document；
 *    sample_rows / sample_values 原始业务数据绝不进入 content，枚举只用人工维护的 display_name。
 * 2. 向量表继承「数据源侧全租户私有」：每行显式 tenant_id、按 datasource_id 校验归属、
 *    不登记 ignore-tables、不加 @TenantIgnore；库里不存在 tenant_id = 0 的共享向量。
 * 3. content_hash = SHA-256(content + "|" + modelCode)，未变且已有向量则跳过，
 *    避免重复消耗租户 Embedding 额度；换模型会因 modelCode 变化自动重建本租户向量。
 * 4. Embedding 由 P2 的 TenantSchemaEmbeddingProvider 按租户动态解析；未配置凭证或显式关闭时
 *    Document 仍完整落库，但 embedding = NULL、sync_status = 0，status 如实暴露待构建数，不伪造向量。
 * 5. 源端失效 / 可见性关闭 / 改为 forbid / 换了模型的旧行，统一标记下线（sync_status = 4）。
 * 6. 维度固定 1536：写入前校验 Provider 维度与返回字面量元素数，防止不同维度混存破坏 HNSW。
 */
@Service
public class SchemaVectorBuildServiceImpl implements SchemaVectorBuildService {

    private static final int VECTOR_DIM = 1536;
    private static final int EMBED_BATCH_SIZE = 32;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_ENUM_DISPLAY = 10;
    private static final int MAX_TABLE_COLUMNS_IN_DOC = 20;

    @Resource
    private AiDatasourceOwnerValidator ownerValidator;
    @Resource
    private AiDatasourceTableMapper tableMapper;
    @Resource
    private AiDatasourceColumnMapper columnMapper;
    @Resource
    private AiDatasourceColumnEnumMapper columnEnumMapper;
    @Resource
    private AiSchemaVectorMapper vectorMapper;
    @Resource
    private AiSchemaVectorService vectorService;
    @Resource
    private AiDatasourceSyncLogService syncLogService;
    @Resource
    private SchemaEmbeddingProvider embeddingProvider;

    // ==================== 构建 ====================

    @Override
    public SchemaVectorBuildRespVO build(Long datasourceId, boolean rebuild) {
        AiDatasource ds = ownerValidator.requireOwned(datasourceId);
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        if (embeddingProvider.dimension() != VECTOR_DIM) {
            throw exception(SCHEMA_VECTOR_DIM_MISMATCH);
        }
        long begin = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        String taskNo = "VEC" + System.currentTimeMillis();
        String modelCode = embeddingProvider.currentModelCode(tenantId);
        boolean available = embeddingProvider.isAvailable(tenantId);
        int structVersion = ds.getStructVersion() == null ? 0 : ds.getStructVersion();
        List<String> warnings = new ArrayList<>();
        if (!available) {
            warnings.add("本租户 Embedding 当前不可用（未配置 EMBEDDING 绑定 / 凭证，"
                    + "或显式关闭了 data-query.embedding.enabled）：Document 已完整入库，"
                    + "embedding 为 NULL、sync_status = 0；配置完成后重新同步自动补齐");
        }
        warnings.add("向量表全租户私有：显式 tenant_id + datasource_id 校验，未登记 ignore-tables");

        int created = 0, updated = 0, skipped = 0, retired = 0, embedded = 0, pending = 0;
        int totalDocuments = 0;
        boolean success = false;
        String errorMsg = "";

        try {
            List<SchemaDocument> docs = buildDocuments(datasourceId, structVersion, modelCode, warnings);
            totalDocuments = docs.size();
            if (docs.isEmpty()) {
                warnings.add("没有可见可查的表与列，未生成任何 Document；请先做元数据同步并维护可见性");
            }

            List<AiSchemaVector> existing = vectorService.listByDatasource(datasourceId);
            Map<String, AiSchemaVector> existingByKey = new HashMap<>();
            for (AiSchemaVector row : existing) {
                existingByKey.put(vectorKey(row), row);
            }
            Set<Long> keepIds = new HashSet<>();
            List<Integer> needWrite = new ArrayList<>();
            for (int i = 0; i < docs.size(); i++) {
                SchemaDocument doc = docs.get(i);
                AiSchemaVector row = existingByKey.get(doc.key() + "|" + modelCode);
                // P1 未接线时向量恒为 NULL，不能以「已有向量」作为跳过前提，否则每次都无意义重写；
                // P2 接入后（available = true）才要求向量已生成，生成失败的行会在下次重试。
                // sync_status = 2（可见性变更标记需重建）也强制重写。
                boolean unchanged = !rebuild && row != null
                        && doc.getContentHash().equals(row.getContentHash())
                        && !Integer.valueOf(2).equals(row.getSyncStatus())
                        && (!available || StringUtils.hasText(row.getEmbedding()));
                if (unchanged) {
                    skipped++;
                    keepIds.add(row.getId());
                } else {
                    needWrite.add(i);
                }
            }

            List<String> vectors = new ArrayList<>(Collections.nCopies(docs.size(), null));
            if (available && !needWrite.isEmpty()) {
                for (int from = 0; from < needWrite.size(); from += EMBED_BATCH_SIZE) {
                    int to = Math.min(from + EMBED_BATCH_SIZE, needWrite.size());
                    List<Integer> batchIdx = needWrite.subList(from, to);
                    List<String> texts = batchIdx.stream()
                            .map(i -> docs.get(i).getContent()).collect(Collectors.toList());
                    List<String> literals = embeddingProvider.embed(tenantId, texts);
                    if (literals == null || literals.size() != texts.size()) {
                        throw new IllegalStateException("Embedding 返回数量与输入不一致");
                    }
                    for (int j = 0; j < batchIdx.size(); j++) {
                        String literal = literals.get(j);
                        if (!StringUtils.hasText(literal) || !checkDimension(literal)) {
                            throw exception(SCHEMA_VECTOR_DIM_MISMATCH);
                        }
                        vectors.set(batchIdx.get(j), literal);
                        embedded++;
                    }
                }
            }

            for (Integer index : needWrite) {
                SchemaDocument doc = docs.get(index);
                String literal = vectors.get(index);
                AiSchemaVector row = existingByKey.get(doc.key() + "|" + modelCode);
                boolean isNew = row == null;
                if (isNew) {
                    row = new AiSchemaVector();
                }
                fill(row, doc, datasourceId, modelCode, structVersion, literal);
                if (isNew) {
                    vectorService.createVector(row);
                    created++;
                } else {
                    vectorService.updateVector(row);
                    updated++;
                }
                if (literal == null) {
                    pending++;
                }
                keepIds.add(row.getId());
            }
            retired = vectorService.retireVectors(datasourceId, keepIds);
            if (retired > 0) {
                warnings.add("下线失效 / 不可见 / forbid / 旧模型向量 " + retired + " 行");
            }
            success = true;
        } catch (Exception ex) {
            errorMsg = safeError(ex);
            warnings.add("构建中断：" + errorMsg);
        }

        long latencyMs = System.currentTimeMillis() - begin;
        int status = success ? (pending > 0 ? 2 : 1) : 3;
        writeLog(ds, tenantId, taskNo, rebuild, startTime, LocalDateTime.now(), latencyMs,
                status, totalDocuments, created, updated, skipped, retired, embedded, pending,
                modelCode, errorMsg, warnings);

        SchemaVectorBuildRespVO resp = new SchemaVectorBuildRespVO();
        resp.setDatasourceId(datasourceId);
        resp.setTaskNo(taskNo);
        resp.setRebuild(rebuild);
        resp.setEmbeddingAvailable(available);
        resp.setModelCode(modelCode);
        resp.setDimension(VECTOR_DIM);
        resp.setTotalDocuments(totalDocuments);
        resp.setCreated(created);
        resp.setUpdated(updated);
        resp.setSkipped(skipped);
        resp.setRetired(retired);
        resp.setEmbedded(embedded);
        resp.setPendingEmbedding(pending);
        resp.setStructVersion(structVersion);
        resp.setLatencyMs(latencyMs);
        resp.setStatus(status);
        resp.setWarnings(warnings);
        return resp;
    }

    // ==================== Document 构建 ====================

    private List<SchemaDocument> buildDocuments(Long datasourceId, int structVersion,
                                                String modelCode, List<String> warnings) {
        List<AiDatasourceTable> tables = tableMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceTable>()
                        .eq(AiDatasourceTable::getDatasourceId, datasourceId)
                        .eq(AiDatasourceTable::getVisible, 1)
                        .eq(AiDatasourceTable::getQueryable, 1)
                        .ne(AiDatasourceTable::getSyncStatus, 4)
                        .eq(AiDatasourceTable::getDeleted, 0));
        List<AiDatasourceColumn> columns = columnMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceColumn>()
                        .eq(AiDatasourceColumn::getDatasourceId, datasourceId)
                        .eq(AiDatasourceColumn::getVisible, 1)
                        .eq(AiDatasourceColumn::getQueryable, 1)
                        .ne(AiDatasourceColumn::getSyncStatus, 4)
                        .eq(AiDatasourceColumn::getDeleted, 0));
        Map<Long, List<String>> enumDisplayNames = loadEnumDisplayNames(datasourceId);

        Map<Long, List<AiDatasourceColumn>> columnsByTable = new LinkedHashMap<>();
        int forbidSkipped = 0;
        for (AiDatasourceColumn column : columns) {
            if ("forbid".equalsIgnoreCase(safe(column.getMaskRule()))) {
                forbidSkipped++;
                continue; // 越权列整列不进向量表
            }
            columnsByTable.computeIfAbsent(column.getTableId(), k -> new ArrayList<>()).add(column);
        }
        if (forbidSkipped > 0) {
            warnings.add("mask_rule = forbid 的列 " + forbidSkipped + " 个已整列排除，不生成向量");
        }

        List<SchemaDocument> docs = new ArrayList<>();
        for (AiDatasourceTable table : tables) {
            List<AiDatasourceColumn> tableColumns = columnsByTable.getOrDefault(table.getId(), List.of());
            SchemaDocument tableDoc = new SchemaDocument();
            tableDoc.setObjectType(1);
            tableDoc.setTableId(table.getId());
            tableDoc.setColumnId(null);
            tableDoc.setSchemaName(safe(table.getSchemaName()));
            tableDoc.setTableName(safe(table.getTableName()));
            tableDoc.setColumnName("");
            tableDoc.setVisible(table.getVisible());
            tableDoc.setQueryable(table.getQueryable());
            tableDoc.setSensitiveType(table.getSensitiveLevel());
            tableDoc.setMaskRule("");
            tableDoc.setStructVersion(structVersion);
            StringBuilder sb = new StringBuilder();
            append(sb, "数据表", table.getTableName());
            append(sb, "中文名", table.getNameZh());
            append(sb, "所属 schema", table.getSchemaName());
            append(sb, "业务说明", table.getDescription());
            append(sb, "默认时间字段", table.getDefaultTimeColumn());
            List<String> names = tableColumns.stream()
                    .limit(MAX_TABLE_COLUMNS_IN_DOC)
                    .map(c -> safe(c.getColumnName())
                            + (StringUtils.hasText(c.getNameZh()) ? "(" + c.getNameZh() + ")" : ""))
                    .collect(Collectors.toList());
            if (!names.isEmpty()) {
                append(sb, "包含字段", String.join("、", names));
            }
            tableDoc.setContent(limit(sb.toString()));
            tableDoc.setContentHash(hash(tableDoc.getContent(), modelCode));
            docs.add(tableDoc);

            for (AiDatasourceColumn column : tableColumns) {
                SchemaDocument columnDoc = new SchemaDocument();
                columnDoc.setObjectType(2);
                columnDoc.setTableId(table.getId());
                columnDoc.setColumnId(column.getId());
                columnDoc.setSchemaName(safe(table.getSchemaName()));
                columnDoc.setTableName(safe(table.getTableName()));
                columnDoc.setColumnName(safe(column.getColumnName()));
                columnDoc.setVisible(column.getVisible());
                columnDoc.setQueryable(column.getQueryable());
                columnDoc.setSensitiveType(column.getSensitiveType());
                columnDoc.setMaskRule(safe(column.getMaskRule()));
                columnDoc.setStructVersion(structVersion);
                StringBuilder csb = new StringBuilder();
                append(csb, "字段", table.getTableName() + "." + column.getColumnName());
                append(csb, "中文名", column.getNameZh());
                append(csb, "数据类型", column.getDataType());
                append(csb, "业务说明", column.getDescription());
                append(csb, "指标口径", column.getMetricDef());
                append(csb, "同义词", column.getSynonyms());
                append(csb, "单位", column.getUnit());
                List<String> displays = enumDisplayNames.getOrDefault(column.getId(), List.of());
                if (!displays.isEmpty()) {
                    append(csb, "枚举含义", String.join("、", displays));
                }
                columnDoc.setContent(limit(csb.toString()));
                columnDoc.setContentHash(hash(columnDoc.getContent(), modelCode));
                docs.add(columnDoc);
            }
        }
        return docs;
    }

    /** 只取人工维护的枚举 display_name，不读 sample_values，不执行任何业务表查询 */
    private Map<Long, List<String>> loadEnumDisplayNames(Long datasourceId) {
        List<AiDatasourceColumnEnum> rows = columnEnumMapper.selectList(
                new LambdaQueryWrapperX<AiDatasourceColumnEnum>()
                        .eq(AiDatasourceColumnEnum::getDatasourceId, datasourceId)
                        .eq(AiDatasourceColumnEnum::getStatus, 0)
                        .eq(AiDatasourceColumnEnum::getDeleted, 0));
        Map<Long, List<String>> result = new HashMap<>();
        for (AiDatasourceColumnEnum row : rows) {
            if (row.getColumnId() == null || !StringUtils.hasText(row.getDisplayName())) {
                continue;
            }
            List<String> list = result.computeIfAbsent(row.getColumnId(), k -> new ArrayList<>());
            if (list.size() < MAX_ENUM_DISPLAY) {
                list.add(row.getDisplayName());
            }
        }
        return result;
    }

    // ==================== 状态 ====================

    @Override
    public SchemaVectorStatusRespVO status(Long datasourceId) {
        AiDatasource ds = ownerValidator.requireOwned(datasourceId);
        Long tenantId = TenantContextHolder.getTenantId();
        boolean available = embeddingProvider.isAvailable(tenantId);
        int structVersion = ds.getStructVersion() == null ? 0 : ds.getStructVersion();

        SchemaVectorStatusRespVO resp = new SchemaVectorStatusRespVO();
        resp.setDatasourceId(datasourceId);
        resp.setEmbeddingAvailable(available);
        resp.setModelCode(embeddingProvider.currentModelCode(tenantId));
        resp.setDimension(embeddingProvider.dimension());
        resp.setVectorCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getDeleted, 0)));
        resp.setBuiltCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getSyncStatus, 1)
                .eq(AiSchemaVector::getDeleted, 0)));
        resp.setPendingCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .ne(AiSchemaVector::getSyncStatus, 1)
                .eq(AiSchemaVector::getDeleted, 0)));
        resp.setRetiredCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .eq(AiSchemaVector::getDeleted, 1)));
        resp.setStaleCount(vectorMapper.selectCount(new LambdaQueryWrapperX<AiSchemaVector>()
                .eq(AiSchemaVector::getDatasourceId, datasourceId)
                .ne(AiSchemaVector::getStructVersion, structVersion)
                .eq(AiSchemaVector::getDeleted, 0)));
        resp.setStructVersion(structVersion);

        List<AiDatasourceSyncLog> logs = syncLogService.listByDatasourceId(datasourceId);
        for (AiDatasourceSyncLog log : logs) {
            if (Integer.valueOf(7).equals(log.getSyncType()) || Integer.valueOf(8).equals(log.getSyncType())) {
                resp.setLastTaskNo(log.getTaskNo());
                resp.setLastBuildTime(log.getEndTime());
                resp.setLastBuildStatus(log.getStatus());
                break;
            }
        }

        List<String> notes = new ArrayList<>();
        if (!available) {
            notes.add("本租户 Embedding 当前不可用：Document 完整入库但向量为 NULL，"
                    + "补齐 EMBEDDING 绑定与供应商凭证后重新同步即可，无平台共享 Key");
        }
        notes.add("每行带真实 tenant_id，SQL 硬过滤 + TenantDatabaseInterceptor 二次保障，严禁登记 ignore-tables");
        notes.add("一期检索建议用「B-tree 预过滤 + 精确距离计算」：全局 HNSW 对 tenant_id 是 post-filter，"
                + "低租户占比可能静默返回不足 Top-K");
        if (resp.getStaleCount() != null && resp.getStaleCount() > 0) {
            notes.add("存在 " + resp.getStaleCount() + " 行 struct_version 落后，建议执行 rebuild");
        }
        resp.setNotes(notes);
        return resp;
    }

    // ==================== 分页 ====================

    @Override
    public PageResult<AiSchemaVectorRespVO> page(AiSchemaVectorPageReqVO reqVO) {
        ownerValidator.requireOwned(reqVO.getDatasourceId());
        IPage<AiSchemaVector> page = vectorService.page(MyBatisUtils.buildPage(reqVO),
                new LambdaQueryWrapperX<AiSchemaVector>()
                        .eq(AiSchemaVector::getDatasourceId, reqVO.getDatasourceId())
                        .eqIfPresent(AiSchemaVector::getObjectType, reqVO.getObjectType())
                        .eqIfPresent(AiSchemaVector::getSyncStatus, reqVO.getSyncStatus())
                        .likeIfPresent(AiSchemaVector::getContent, reqVO.getKeyword())
                        .eq(AiSchemaVector::getDeleted, 0)
                        .orderByDesc(AiSchemaVector::getId));
        List<AiSchemaVectorRespVO> list = page.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    private AiSchemaVectorRespVO toVO(AiSchemaVector row) {
        AiSchemaVectorRespVO vo = new AiSchemaVectorRespVO();
        vo.setId(row.getId());
        vo.setDatasourceId(row.getDatasourceId());
        vo.setObjectType(row.getObjectType());
        vo.setTableId(row.getTableId());
        vo.setColumnId(row.getColumnId());
        vo.setSchemaName(row.getSchemaName());
        vo.setTableName(row.getTableName());
        vo.setColumnName(row.getColumnName());
        String content = safe(row.getContent());
        vo.setContentPreview(content.length() <= 200 ? content : content.substring(0, 200) + "...");
        vo.setContentHash(row.getContentHash());
        vo.setEmbeddingModelCode(row.getEmbeddingModelCode());
        vo.setEmbeddingDim(row.getEmbeddingDim());
        vo.setEmbedded(StringUtils.hasText(row.getEmbedding()));
        vo.setStructVersion(row.getStructVersion());
        vo.setVisible(row.getVisible());
        vo.setQueryable(row.getQueryable());
        vo.setSensitiveType(row.getSensitiveType());
        vo.setMaskRule(row.getMaskRule());
        vo.setSyncStatus(row.getSyncStatus());
        return vo;
    }

    // ==================== 辅助 ====================

    private void fill(AiSchemaVector row, SchemaDocument doc, Long datasourceId,
                      String modelCode, int structVersion, String literal) {
        row.setDatasourceId(datasourceId);
        row.setObjectType(doc.getObjectType());
        row.setTableId(doc.getTableId());
        row.setColumnId(doc.getColumnId());
        row.setSchemaName(doc.getSchemaName());
        row.setTableName(doc.getTableName());
        row.setColumnName(doc.getColumnName());
        row.setContent(doc.getContent());
        row.setContentHash(doc.getContentHash());
        row.setEmbedding(literal);
        row.setEmbeddingModelCode(modelCode);
        row.setEmbeddingDim(VECTOR_DIM);
        row.setStructVersion(structVersion);
        row.setVisible(doc.getVisible());
        row.setQueryable(doc.getQueryable());
        row.setSensitiveType(doc.getSensitiveType() == null ? 0 : doc.getSensitiveType());
        row.setMaskRule(safe(doc.getMaskRule()));
        row.setSyncStatus(literal == null ? 0 : 1);
        row.setStatus(0);
    }

    private void writeLog(AiDatasource ds, Long tenantId, String taskNo, boolean rebuild,
                          LocalDateTime startTime, LocalDateTime endTime, long latencyMs,
                          int status, int totalDocuments, int created, int updated, int skipped,
                          int retired, int embedded, int pending, String modelCode,
                          String errorMsg, List<String> warnings) {
        AiDatasourceSyncLog log = new AiDatasourceSyncLog();
        log.setDatasourceId(ds.getId());
        log.setTaskNo(taskNo);
        log.setSyncType(rebuild ? 8 : 7); // 7 向量增量构建 / 8 全量重建
        log.setTriggerType(1);
        log.setScope("Schema 向量索引 model=" + modelCode);
        log.setStartTime(startTime);
        log.setEndTime(endTime);
        log.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        log.setStatus(status);
        log.setTableAdded(0);
        log.setTableChanged(0);
        log.setTableRemoved(0);
        log.setColumnAdded(0);
        log.setColumnChanged(0);
        log.setColumnRemoved(0);
        log.setRelationAdded(0);
        log.setEnumAdded(0);
        List<String> details = new ArrayList<>(warnings);
        details.add("Document " + totalDocuments + " / 新建 " + created + " / 更新 " + updated
                + " / 跳过 " + skipped + " / 下线 " + retired
                + " / 生成向量 " + embedded + " / 待构建 " + pending);
        log.setChangeDetail(truncate(String.join("；", details), 2000));
        log.setStructVersionBefore(ds.getStructVersion() == null ? 0 : ds.getStructVersion());
        log.setStructVersionAfter(ds.getStructVersion() == null ? 0 : ds.getStructVersion());
        log.setErrorCode("");
        log.setErrorMsg(truncate(errorMsg, 2000));
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        log.setOperatorId(userId == null ? 0L : userId);
        log.setOperatorName(userId == null ? "" : userId.toString());
        log.setTenantId(tenantId);
        syncLogService.createLog(log);
    }

    private static String vectorKey(AiSchemaVector row) {
        return row.getObjectType() + ":" + (row.getTableId() == null ? 0 : row.getTableId())
                + ":" + (row.getColumnId() == null ? 0 : row.getColumnId())
                + "|" + safe(row.getEmbeddingModelCode());
    }

    /** pgvector 字面量 "[0.1,0.2,...]" 的元素数必须等于 1536 */
    private static boolean checkDimension(String literal) {
        String value = literal.trim();
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return false;
        }
        String body = value.substring(1, value.length() - 1);
        if (body.isEmpty()) {
            return false;
        }
        int count = 1;
        for (char c : body.toCharArray()) {
            if (c == ',') {
                count++;
            }
        }
        return count == VECTOR_DIM;
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("；");
        }
        sb.append(label).append("：").append(value.trim());
    }

    private static String limit(String value) {
        return value.length() <= MAX_CONTENT_LENGTH ? value : value.substring(0, MAX_CONTENT_LENGTH);
    }

    private static String hash(String content, String modelCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (content + "|" + modelCode).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

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
                return "向量构建失败（" + ex.getClass().getSimpleName()
                        + "；含非 UTF-8 字符，已省略原文）";
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

}
