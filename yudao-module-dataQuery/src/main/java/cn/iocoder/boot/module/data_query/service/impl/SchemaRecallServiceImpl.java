package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumnEnum;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTableRelation;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaRecallProperties;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaVectorHit;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaEmbeddingProvider;
import cn.iocoder.boot.module.data_query.mapper.AiSchemaVectorMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceColumnEnumService;
import cn.iocoder.boot.module.data_query.service.AiDatasourceColumnService;
import cn.iocoder.boot.module.data_query.service.AiDatasourceService;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableRelationService;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableService;
import cn.iocoder.boot.module.data_query.service.SchemaRecallService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_ID_REQUIRED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_RECALL_QUESTION_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * S3 Schema 召回实现（P3，一期精确扫描 + RRF 混合重排 + 关键词兜底）。
 *
 * 流水线：
 * 租户/归属校验 → 问题归一化与关键词提取 → 两路召回（各带 tenant_id 硬过滤）
 * → RRF 融合 → 表聚合 Top-K → <b>PG 元数据二次核验</b> → 列裁剪 / Join / 枚举 / 口径
 * → Token 预算裁剪 → SchemaContext（S6 白名单 + S4 变量来源）。
 *
 * 安全红线（不可配置关闭）：
 * 1. tenantId 只取 TenantContextHolder，前端传参一律忽略；
 * 2. 向量路与关键词路 SQL 都显式带 tenant_id 条件，并由 TenantDatabaseInterceptor 再追加一次；
 * 3. query vector 必须由当前租户自己的 Embedding 模型生成，绝不借用平台或其它租户模型；
 * 4. 召回结果再用 PG 元数据核一次 visible / queryable / sync_status / forbid，形成 S6 白名单；
 * 5. 任何降级都只降低「召回方式」，不放宽以上过滤，不把全库 Schema 硬塞给模型。
 */
@Slf4j
@Service
public class SchemaRecallServiceImpl implements SchemaRecallService {

    /** 中文停用词：只用于关键词路，避免「查询 / 统计 / 的」之类噪声撑满 ILIKE 分支 */
    private static final Set<String> STOP_WORDS = Set.of(
            "查询", "统计", "请问", "帮我", "一下", "多少", "汇总", "显示", "列出", "计算",
            "数据", "报表", "情况", "所有", "每个", "各个", "分别", "按照", "根据", "以及",
            "时间", "日期", "今年", "去年", "本月", "上月", "最近", "趋势", "对比", "排行");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9_]+");
    private static final Pattern ASCII_WORD = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{1,}");
    private static final Pattern ILIKE_UNSAFE = Pattern.compile("[%_\\\\'\"\\s]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 表角色：1 事实表（见 nl2sql-datasource.sql 的 COMMENT） */
    private static final int TABLE_ROLE_FACT = 1;
    /** 同步状态：4 源端已删除（失效） */
    private static final int SYNC_STATUS_RETIRED = 4;

    @Resource
    private AiSchemaVectorMapper vectorMapper;
    @Resource
    private SchemaEmbeddingProvider embeddingProvider;
    @Resource
    private SchemaRecallProperties properties;
    @Resource
    private AiDatasourceService datasourceService;
    @Resource
    private AiDatasourceTableService tableService;
    @Resource
    private AiDatasourceColumnService columnService;
    @Resource
    private AiDatasourceTableRelationService relationService;
    @Resource
    private AiDatasourceColumnEnumService enumService;

    @Override
    public SchemaContext recall(Long datasourceId, String question, Integer topK) {
        long started = System.currentTimeMillis();

        // ---- 0. 参数与租户边界：前端无法传 tenantId，只能来自服务端鉴权上下文 ----
        if (datasourceId == null) {
            throw exception(DATASOURCE_ID_REQUIRED);
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        String normalized = normalize(question);
        if (normalized.isEmpty()) {
            throw exception(SCHEMA_RECALL_QUESTION_EMPTY);
        }
        AiDatasource datasource = datasourceService.getDatasourceOwned(datasourceId);

        // ---- 1. 两路召回 ----
        List<SchemaVectorHit> vectorHits = Collections.emptyList();
        List<SchemaVectorHit> keywordHits = Collections.emptyList();
        boolean vectorAttempted = false;
        boolean vectorUsable = false;
        boolean degraded = false;
        String degradeReason = null;
        String modelCode = null;
        Integer dimension = null;

        List<String> keywords = extractKeywords(normalized);

        // 1.1 向量路：可用性 → query vector → 精确扫描；任一失败都只降级，不中断召回
        try {
            if (embeddingProvider.isAvailable(tenantId)) {
                dimension = embeddingProvider.dimension();
                modelCode = safe(embeddingProvider.currentModelCode(tenantId));
                List<String> literals = embeddingProvider.embed(tenantId, Collections.singletonList(normalized));
                if (literals == null || literals.isEmpty() || !hasText(literals.get(0))) {
                    throw new IllegalStateException("Embedding 返回空向量");
                }
                String queryVector = literals.get(0);
                vectorAttempted = true;
                // 每类（表 / 列）各取 N 条：列级 Document 数量远多于表级，共用 LIMIT 时
                // 宽表的列向量会占满候选池，导致 Top-K 表只聚合出 1 张（详见 Mapper 注释）
                vectorHits = vectorMapper.searchByVector(tenantId, datasourceId, queryVector,
                        positive(properties.getVectorCandidates(), 20));
                vectorUsable = true;
                log.debug("[recall] 向量路命中 {} 条（表 {} / 列 {}）, tenantId={}, datasourceId={}, model={}",
                        vectorHits.size(), countByType(vectorHits, 1), countByType(vectorHits, 2),
                        tenantId, datasourceId, modelCode);
            } else {
                degraded = true;
                degradeReason = "当前租户未配置可用的 Embedding 模型或凭证，已降级为关键词召回";
            }
        } catch (Exception ex) {
            degraded = true;
            degradeReason = "Embedding 调用或 pgvector 检索失败，已降级为关键词召回：" + brief(ex);
            log.warn("[recall] 向量路降级, tenantId={}, datasourceId={}, reason={}",
                    tenantId, datasourceId, degradeReason);
        }

        // 1.2 关键词路：既是混合重排的一路信号，也是降级时的兜底召回
        boolean keywordOk = false;
        try {
            if (!keywords.isEmpty() && (vectorUsable || Boolean.TRUE.equals(properties.getKeywordFallbackEnabled()))) {
                // 同样按「每类」上限取，关键词路与向量路口径一致
                keywordHits = vectorMapper.searchByKeyword(tenantId, datasourceId, keywords,
                        positive(properties.getKeywordCandidates(), 20));
            }
            keywordOk = true;
        } catch (Exception ex) {
            log.warn("[recall] 关键词路失败, tenantId={}, datasourceId={}, reason={}",
                    tenantId, datasourceId, brief(ex));
        }

        int vectorCount = vectorHits.size();
        int keywordCount = keywordHits.size();
        if (vectorCount == 0 && keywordCount == 0) {
            SchemaContext empty = SchemaContext.empty(datasourceId, normalized,
                    (degraded ? "KEYWORD_DEGRADED" : "EMPTY"), degraded,
                    degraded ? degradeReason : "未召回到任何可见可查的表或字段，建议澄清问题或改用推荐问题");
            fillDatasource(empty, datasource);
            empty.setEmbeddingModelCode(modelCode);
            empty.setDimension(dimension);
            empty.setLatencyMs(System.currentTimeMillis() - started);
            empty.setVectorCandidateCount(vectorCount);
            empty.setKeywordCandidateCount(keywordCount);
            if (!keywordOk) {
                // 两路都异常：如实告知，不伪造空结果当作「无相关表」
                empty.setDegradeReason("向量路与关键词路均不可用：" + safe(degradeReason));
                empty.setDegraded(true);
            }
            return empty;
        }

        // ---- 2. RRF 融合（同一对象可能两路都命中，按 key 归并后累加倒数秩次） ----
        Map<String, Candidate> candidates = fuse(vectorHits, keywordHits);

        // ---- 3. 表聚合：列级命中折算后归到表，表级命中直接计入 ----
        Map<Long, Double> tableScores = aggregateTableScores(candidates);

        // ---- 4. PG 元数据二次核验：白名单只取自「可见可查且未下线」的元数据 ----
        Map<Long, AiDatasourceTable> visibleTables = loadVisibleTables(datasourceId);
        Map<Long, List<AiDatasourceColumn>> visibleColumns = loadVisibleColumns(datasourceId);
        if (visibleTables.isEmpty()) {
            SchemaContext empty = SchemaContext.empty(datasourceId, normalized, "EMPTY", degraded,
                    "PG 元数据中没有可见可查的表（向量索引可能已过期），请先执行元数据同步");
            fillDatasource(empty, datasource);
            empty.setLatencyMs(System.currentTimeMillis() - started);
            empty.setVectorCandidateCount(vectorCount);
            empty.setKeywordCandidateCount(keywordCount);
            return empty;
        }

        // 事实表加权，并剔除 PG 元数据里已不可见 / 已下线的表
        List<Map.Entry<Long, Double>> rankedTables = tableScores.entrySet().stream()
                .filter(e -> visibleTables.containsKey(e.getKey()))
                .map(e -> {
                    AiDatasourceTable table = visibleTables.get(e.getKey());
                    double score = e.getValue();
                    if (table.getTableRole() != null && table.getTableRole() == TABLE_ROLE_FACT) {
                        score *= (1.0 + safeDouble(properties.getFactTableBoost()));
                    }
                    return Map.entry(e.getKey(), score);
                })
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toList());

        int k = positive(topK != null ? topK : properties.getTopKTables(), 6);
        List<Long> selectedTableIds = rankedTables.stream()
                .limit(k).map(Map.Entry::getKey).collect(Collectors.toList());

        if (selectedTableIds.isEmpty()) {
            SchemaContext empty = SchemaContext.empty(datasourceId, normalized, "EMPTY", degraded,
                    "召回候选经 PG 元数据二次核验后全部被过滤（不可见 / 不可查 / 已下线）");
            fillDatasource(empty, datasource);
            empty.setLatencyMs(System.currentTimeMillis() - started);
            empty.setVectorCandidateCount(vectorCount);
            empty.setKeywordCandidateCount(keywordCount);
            return empty;
        }

        // ---- 4.5 一跳关联扩展：把外键另一端的维表拉进选集 ----
        // 典型场景：问「谁用过会议室」只命中预约表；借 booking.user_id → oa_user.id
        // 把用户表带进来，模型才能 JOIN 取 real_name，而不是只返回 user_id。
        // 安全：扩展表必须同样来自 visibleTables（PG 可见可查二次核验）；只做一跳不递归；
        //       数量受 maxExpandTables 限制；开关 relationExpandEnabled 可关。
        List<Long> expandedTableIds = expandByRelations(datasourceId, selectedTableIds,
                visibleTables, visibleColumns);

        // ---- 5. 组装 SchemaContext ----
        SchemaContext ctx = new SchemaContext();
        fillDatasource(ctx, datasource);
        ctx.setDatasourceId(datasourceId);
        ctx.setQuestion(normalized);
        ctx.setEmbeddingModelCode(modelCode);
        ctx.setDimension(dimension);
        ctx.setDegraded(degraded);
        ctx.setDegradeReason(degraded ? degradeReason : null);
        ctx.setRecommendClarify(false);
        ctx.setVectorCandidateCount(vectorCount);
        ctx.setKeywordCandidateCount(keywordCount);
        ctx.setRecallMode(resolveMode(vectorUsable, vectorAttempted, vectorCount, keywordCount, degraded));
        ctx.setTruncated(false);

        Set<Long> selectedSet = new LinkedHashSet<>(selectedTableIds);
        Map<Long, Double> columnScoreByTable = columnScores(candidates);

        // 5.1 表与列：命中列优先，再按 sort / ordinal_position 补足；每表硬裁剪
        int maxColumns = positive(properties.getMaxColumnsPerTable(), 20);
        for (Long tableId : selectedTableIds) {
            AiDatasourceTable table = visibleTables.get(tableId);
            SchemaContext.TableItem item = new SchemaContext.TableItem();
            item.setTableId(tableId);
            item.setSchemaName(safe(table.getSchemaName()));
            item.setTableName(safe(table.getTableName()));
            item.setNameZh(safe(table.getNameZh()));
            item.setDescription(safe(table.getDescription()));
            item.setTableRole(table.getTableRole());
            item.setDefaultTimeColumn(safe(table.getDefaultTimeColumn()));
            item.setScore(round(tableScores.getOrDefault(tableId, 0.0)));

            List<AiDatasourceColumn> columns = new ArrayList<>(
                    visibleColumns.getOrDefault(tableId, Collections.emptyList()));
            // forbid 列整列排除（与向量写入侧一致）
            columns.removeIf(c -> "forbid".equalsIgnoreCase(safe(c.getMaskRule())));
            final Map<Long, Double> hitScore = columnScoreByTable;
            columns.sort(Comparator
                    .comparing((AiDatasourceColumn c) -> -hitScore.getOrDefault(c.getId(), 0.0))
                    .thenComparing(c -> c.getSort() == null ? Integer.MAX_VALUE : c.getSort())
                    .thenComparing(c -> c.getOrdinalPosition() == null ? Integer.MAX_VALUE : c.getOrdinalPosition())
                    .thenComparing(AiDatasourceColumn::getId));
            if (columns.size() > maxColumns) {
                ctx.setTruncated(true);
                columns = columns.subList(0, maxColumns);
            }
            for (AiDatasourceColumn column : columns) {
                SchemaContext.ColumnItem ci = new SchemaContext.ColumnItem();
                ci.setColumnId(column.getId());
                ci.setColumnName(safe(column.getColumnName()));
                ci.setNameZh(safe(column.getNameZh()));
                ci.setDataType(safe(column.getDataType()));
                ci.setUnit(safe(column.getUnit()));
                ci.setMetricDef(safe(column.getMetricDef()));
                ci.setSynonyms(safe(column.getSynonyms()));
                ci.setSensitiveType(column.getSensitiveType());
                ci.setMaskRule(safe(column.getMaskRule()));
                ci.setOrdinalPosition(column.getOrdinalPosition());
                ci.setSort(column.getSort());
                ci.setScore(round(hitScore.getOrDefault(column.getId(), 0.0)));
                item.getColumns().add(ci);
                ctx.getColumnIds().add(column.getId());
                if (hasText(column.getMetricDef())) {
                    ctx.getMetricDefs().add(column.getMetricDef().trim());
                }
                if (hasText(column.getSynonyms())) {
                    ctx.getSynonyms().add(column.getSynonyms().trim());
                }
            }
            ctx.getTables().add(item);
            ctx.getTableIds().add(tableId);
        }

        // 5.2 枚举映射：一次按数据源取全部，再按入选列分组，避免 N+1
        // 白名单已在 5.1 固化，枚举查询即使返回未入选列的数据也不会放宽边界
        Map<Long, List<AiDatasourceColumnEnum>> enumsByColumn = loadEnums(datasourceId);
        for (SchemaContext.TableItem item : ctx.getTables()) {
            for (SchemaContext.ColumnItem column : item.getColumns()) {
                for (AiDatasourceColumnEnum e : enumsByColumn.getOrDefault(column.getColumnId(), Collections.emptyList())) {
                    SchemaContext.EnumItem ei = new SchemaContext.EnumItem();
                    ei.setSourceValue(safe(e.getSourceValue()));
                    ei.setDisplayName(safe(e.getDisplayName()));
                    column.getEnums().add(ei);
                }
            }
        }

        // 5.3 Join 路径：只保留已验证、已启用、左右表都在候选集内的
        for (AiDatasourceTableRelation r : relationService.listVerifiedRelations(datasourceId, selectedSet)) {
            if (!selectedSet.contains(r.getLeftTableId()) || !selectedSet.contains(r.getRightTableId())) {
                continue;
            }
            SchemaContext.JoinItem ji = new SchemaContext.JoinItem();
            ji.setLeftTableId(r.getLeftTableId());
            ji.setRightTableId(r.getRightTableId());
            ji.setLeftTableName(safe(r.getLeftTableName()));
            ji.setRightTableName(safe(r.getRightTableName()));
            ji.setJoinType(r.getJoinType());
            ji.setJoinPairs(safe(r.getJoinPairs()));
            ji.setJoinCondition(safe(r.getJoinCondition()));
            ji.setCost(r.getCost());
            ji.setVerified(r.getVerified());
            ctx.getJoins().add(ji);
        }

        ctx.setRelationExpandedTableIds(expandedTableIds);

        // 5.4 命中记录（限制条数，避免响应过大）
        List<Candidate> topCandidates = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> -c.score)
                        .thenComparing(c -> c.key))
                .limit(50).collect(Collectors.toList());
        for (Candidate c : topCandidates) {
            SchemaContext.HitItem hit = new SchemaContext.HitItem();
            hit.setVectorId(c.id);
            hit.setObjectType(c.objectType);
            hit.setTableId(c.tableId);
            hit.setColumnId(c.columnId);
            hit.setTableName(c.tableName);
            hit.setColumnName(c.columnName);
            hit.setDistance(c.distance == null ? null : round(c.distance));
            hit.setKeywordScore(c.keywordScore == null ? null : round(c.keywordScore));
            hit.setRrfScore(round(c.score));
            hit.setSource(c.source);
            hit.setVectorRank(c.vectorRank);
            hit.setKeywordRank(c.keywordRank);
            ctx.getHits().add(hit);
        }

        // 5.5 Schema 文本 + Token 预算裁剪
        buildSchemaText(ctx);

        ctx.setHitTableCount(ctx.getTables().size());
        ctx.setLatencyMs(System.currentTimeMillis() - started);
        return ctx;
    }

    // ==================== 两路召回与融合 ====================

    /** RRF：score = Σ weight / (K + rank)，同一对象两路命中时累加 */
    private Map<String, Candidate> fuse(List<SchemaVectorHit> vectorHits, List<SchemaVectorHit> keywordHits) {
        Map<String, Candidate> merged = new LinkedHashMap<>();
        int k = positive(properties.getRrfK(), 60);
        double vw = safeDouble(properties.getVectorWeight());
        double kw = safeDouble(properties.getKeywordWeight());
        for (int i = 0; i < vectorHits.size(); i++) {
            SchemaVectorHit h = vectorHits.get(i);
            Candidate c = merged.computeIfAbsent(h.key(), key -> Candidate.of(h));
            c.vectorRank = i + 1;
            c.distance = h.getDistance();
            c.score += vw / (k + (double) c.vectorRank);
            c.source = "VECTOR";
        }
        for (int i = 0; i < keywordHits.size(); i++) {
            SchemaVectorHit h = keywordHits.get(i);
            Candidate c = merged.computeIfAbsent(h.key(), key -> Candidate.of(h));
            c.keywordRank = i + 1;
            c.keywordScore = h.getKeywordScore();
            c.score += kw / (k + (double) c.keywordRank);
            c.source = c.vectorRank == null ? "KEYWORD" : "BOTH";
        }
        return merged;
    }

    /** 列级命中按折算系数归到表，表级命中直接计入 */
    private Map<Long, Double> aggregateTableScores(Map<String, Candidate> candidates) {
        Map<Long, Double> scores = new HashMap<>();
        double columnWeight = safeDouble(properties.getColumnToTableWeight());
        for (Candidate c : candidates.values()) {
            if (c.tableId == null) {
                continue;
            }
            double contribution = c.score;
            if (c.objectType != null && c.objectType == 2) {
                contribution *= columnWeight;
            }
            scores.merge(c.tableId, contribution, Double::sum);
        }
        return scores;
    }

    /** 列级命中分，用于列裁剪时把被召回的列排在前面 */
    private Map<Long, Double> columnScores(Map<String, Candidate> candidates) {
        Map<Long, Double> scores = new HashMap<>();
        for (Candidate c : candidates.values()) {
            if (c.objectType != null && c.objectType == 2 && c.columnId != null) {
                scores.merge(c.columnId, c.score, Double::sum);
            }
        }
        return scores;
    }

    private String resolveMode(boolean vectorUsable, boolean vectorAttempted, int vectorCount,
                               int keywordCount, boolean degraded) {
        if (degraded || !vectorUsable) {
            return "KEYWORD_DEGRADED";
        }
        if (keywordCount > 0) {
            return "HYBRID";
        }
        return "VECTOR";
    }

    // ==================== 一跳关联扩展 ====================

    /**
     * 一跳关联扩展：候选表通过「已验证 + 已启用」的外键关系，把另一端的维表拉进选集。
     *
     * 为什么是一跳：只从「本轮 Top-K 直接命中的表」出发，不再递归扩展新加入的表，
     * 防止一条 relation 链把整库维表拖进 Schema / 白名单。
     *
     * 安全边界（不可绕过）：
     * 1. 关联关系必须 verified=1 / enabled=1 / status=0 / deleted=0（service 层已过滤）；
     * 2. 关联表必须同样通过 visibleTables 二次核验（可见 / 可查 / 未下线 / 本租户）；
     * 3. 关联表必须有可见可查的列，否则加进来也是空壳，无意义；
     * 4. 数量受 maxExpandTables 上限约束；总开关 relationExpandEnabled 可关；
     * 5. 只扩充「给模型看 + S6 白名单」的选集，不放宽任何 tenant / visible 条件。
     *
     * @return 被扩展进来的表 ID（有序），供可观测与 SSE 事件透出
     */
    private List<Long> expandByRelations(Long datasourceId, List<Long> selectedTableIds,
                                         Map<Long, AiDatasourceTable> visibleTables,
                                         Map<Long, List<AiDatasourceColumn>> visibleColumns) {
        List<Long> expanded = new ArrayList<>();
        if (!Boolean.TRUE.equals(properties.getRelationExpandEnabled()) || selectedTableIds.isEmpty()) {
            return expanded;
        }
        int maxExpand = positive(properties.getMaxExpandTables(), 2);
        try {
            List<AiDatasourceTableRelation> relations =
                    relationService.listVerifiedRelationsForExpand(datasourceId);
            if (relations.isEmpty()) {
                return expanded;
            }
            // 快照：只用「本轮直接命中的表」作为扩展源，避免递归 / 链式扩展
            Set<Long> seed = new LinkedHashSet<>(selectedTableIds);
            Set<Long> known = new LinkedHashSet<>(selectedTableIds);

            for (AiDatasourceTableRelation r : relations) {
                if (expanded.size() >= maxExpand) {
                    break;
                }
                Long left = r.getLeftTableId();
                Long right = r.getRightTableId();
                Long other = null;
                Long anchor = null;
                if (left != null && seed.contains(left) && right != null && !known.contains(right)) {
                    other = right;
                    anchor = left;
                } else if (right != null && seed.contains(right) && left != null && !known.contains(left)) {
                    other = left;
                    anchor = right;
                }
                if (other == null) {
                    continue;
                }
                // 二次核验：关联表必须可见可查且有列
                if (!visibleTables.containsKey(other)) {
                    continue;
                }
                List<AiDatasourceColumn> cols = visibleColumns.get(other);
                if (cols == null || cols.isEmpty()) {
                    continue;
                }
                selectedTableIds.add(other);
                known.add(other);
                expanded.add(other);
                log.info("[recall] 一跳关联扩展: datasourceId={}, expanded={}, anchor={}, joinPairs={}",
                        datasourceId, other, anchor, safe(r.getJoinPairs()));
            }
        } catch (Exception ex) {
            // 关联扩展是召回增强，取不到不应阻断主召回
            log.warn("[recall] 一跳关联扩展失败, datasourceId={}, reason={}", datasourceId, brief(ex));
        }
        return expanded;
    }

    // ==================== PG 元数据二次核验 ====================

    private Map<Long, AiDatasourceTable> loadVisibleTables(Long datasourceId) {
        Map<Long, AiDatasourceTable> map = new LinkedHashMap<>();
        for (AiDatasourceTable t : tableService.listVisibleTables(datasourceId)) {
            if (isUsableTable(t)) {
                map.put(t.getId(), t);
            }
        }
        return map;
    }

    private Map<Long, List<AiDatasourceColumn>> loadVisibleColumns(Long datasourceId) {
        Map<Long, List<AiDatasourceColumn>> map = new LinkedHashMap<>();
        for (AiDatasourceColumn c : columnService.listVisibleColumnsByDatasource(datasourceId)) {
            if (!isUsableColumn(c)) {
                continue;
            }
            map.computeIfAbsent(c.getTableId(), key -> new ArrayList<>()).add(c);
        }
        return map;
    }

    private boolean isUsableTable(AiDatasourceTable t) {
        return t.getVisible() != null && t.getVisible() == 1
                && t.getQueryable() != null && t.getQueryable() == 1
                && (t.getSyncStatus() == null || t.getSyncStatus() != SYNC_STATUS_RETIRED)
                && (t.getDeleted() == null || t.getDeleted() == 0);
    }

    private boolean isUsableColumn(AiDatasourceColumn c) {
        return c.getVisible() != null && c.getVisible() == 1
                && c.getQueryable() != null && c.getQueryable() == 1
                && (c.getSyncStatus() == null || c.getSyncStatus() != SYNC_STATUS_RETIRED)
                && (c.getDeleted() == null || c.getDeleted() == 0)
                && !"forbid".equalsIgnoreCase(safe(c.getMaskRule()));
    }

    private Map<Long, List<AiDatasourceColumnEnum>> loadEnums(Long datasourceId) {
        Map<Long, List<AiDatasourceColumnEnum>> map = new LinkedHashMap<>();
        try {
            for (AiDatasourceColumnEnum e : enumService.listByDatasourceId(datasourceId)) {
                if (e.getStatus() != null && e.getStatus() != 0) {
                    continue; // 1 停用
                }
                if (e.getDeleted() != null && e.getDeleted() != 0) {
                    continue;
                }
                map.computeIfAbsent(e.getColumnId(), key -> new ArrayList<>()).add(e);
            }
        } catch (Exception ex) {
            // 枚举是增强信息，取不到不应阻断召回
            log.warn("[recall] 枚举映射读取失败, datasourceId={}, reason={}", datasourceId, brief(ex));
        }
        return map;
    }

    // ==================== Schema 文本与 Token 裁剪 ====================

    private void buildSchemaText(SchemaContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- 只允许使用下列表与列；禁止引用未列出的对象，禁止任何写操作\n");
        sb.append("-- 方言: ").append(safe(ctx.getDialect()))
                .append(" | struct_version: ").append(ctx.getStructVersion() == null ? 0 : ctx.getStructVersion())
                .append('\n');

        int budget = positive(properties.getSchemaTokenBudget(), 6000);
        int used = estimateTokens(sb.toString());
        boolean truncated = Boolean.TRUE.equals(ctx.getTruncated());
        List<SchemaContext.TableItem> kept = new ArrayList<>();
        Set<Long> keptTableIds = new LinkedHashSet<>();
        Set<Long> keptColumnIds = new LinkedHashSet<>();

        outer:
        for (SchemaContext.TableItem table : ctx.getTables()) {
            StringBuilder block = new StringBuilder();
            block.append("\nTABLE ").append(qualified(table.getSchemaName(), table.getTableName()));
            if (hasText(table.getNameZh())) {
                block.append(" (").append(table.getNameZh()).append(')');
            }
            if (table.getTableRole() != null && table.getTableRole() == TABLE_ROLE_FACT) {
                block.append(" [事实表]");
            }
            block.append('\n');
            if (hasText(table.getDescription())) {
                block.append("  -- 说明: ").append(singleLine(table.getDescription())).append('\n');
            }
            if (hasText(table.getDefaultTimeColumn())) {
                block.append("  -- 默认时间字段: ").append(table.getDefaultTimeColumn()).append('\n');
            }
            for (SchemaContext.ColumnItem column : table.getColumns()) {
                block.append("  COLUMN ").append(safe(column.getColumnName()));
                if (hasText(column.getDataType())) {
                    block.append(' ').append(column.getDataType());
                }
                StringBuilder comment = new StringBuilder();
                if (hasText(column.getNameZh())) {
                    comment.append(column.getNameZh());
                }
                if (hasText(column.getMetricDef())) {
                    appendPart(comment, "指标口径", column.getMetricDef());
                }
                if (hasText(column.getSynonyms())) {
                    appendPart(comment, "同义词", column.getSynonyms());
                }
                if (hasText(column.getUnit())) {
                    appendPart(comment, "单位", column.getUnit());
                }
                if (!column.getEnums().isEmpty()) {
                    String enums = column.getEnums().stream()
                            .limit(10)
                            .map(e -> safe(e.getSourceValue()) + "=" + safe(e.getDisplayName()))
                            .collect(Collectors.joining(", "));
                    appendPart(comment, "枚举", enums);
                }
                if (comment.length() > 0) {
                    block.append(" -- ").append(singleLine(comment.toString()));
                }
                block.append('\n');
            }

            int cost = estimateTokens(block.toString());
            // 外键一跳扩展带进来的表是「回答用户问题所必需」的（如取 real_name），
            // 不能因 Token 预算被优先裁掉，否则功能静默失效。其余表照常按预算裁剪。
            boolean expandedTable = ctx.getRelationExpandedTableIds().contains(table.getTableId());
            if (!expandedTable && !kept.isEmpty() && used + cost > budget) {
                truncated = true;
                break outer;
            }
            sb.append(block);
            used += cost;
            kept.add(table);
            keptTableIds.add(table.getTableId());
            for (SchemaContext.ColumnItem column : table.getColumns()) {
                keptColumnIds.add(column.getColumnId());
            }
        }

        // 被 Token 预算裁掉的表/列必须同步从白名单移除，否则 S6 会放行未出现在 Schema 里的对象
        if (truncated) {
            ctx.getTables().retainAll(kept);
            ctx.getTableIds().retainAll(keptTableIds);
            ctx.getColumnIds().retainAll(keptColumnIds);
            ctx.setMetricDefs(ctx.getTables().stream().flatMap(t -> t.getColumns().stream())
                    .map(SchemaContext.ColumnItem::getMetricDef).filter(SchemaRecallServiceImpl::hasText)
                    .distinct().collect(Collectors.toList()));
            ctx.setSynonyms(ctx.getTables().stream().flatMap(t -> t.getColumns().stream())
                    .map(SchemaContext.ColumnItem::getSynonyms).filter(SchemaRecallServiceImpl::hasText)
                    .distinct().collect(Collectors.toList()));
            ctx.getJoins().removeIf(j -> !keptTableIds.contains(j.getLeftTableId())
                    || !keptTableIds.contains(j.getRightTableId()));
        }

        if (!ctx.getJoins().isEmpty()) {
            sb.append("\n-- 已验证 Join 路径（左右表均在上述集合内）\n");
            for (SchemaContext.JoinItem j : ctx.getJoins()) {
                sb.append("JOIN ").append(safe(j.getLeftTableName()))
                        .append(" <-> ").append(safe(j.getRightTableName()))
                        .append(" | type=").append(j.getJoinType() == null ? 0 : j.getJoinType())
                        .append(" | pairs=").append(singleLine(safe(j.getJoinPairs())))
                        .append('\n');
            }
        }
        if (truncated) {
            sb.append("\n-- [已按 Token 预算裁剪，部分表/列未列出，禁止引用]\n");
        }
        ctx.setSchemaText(sb.toString());
        ctx.setTokenEstimate(estimateTokens(sb.toString()));
        ctx.setTruncated(truncated);
        ctx.setHitTableCount(ctx.getTables().size());
    }

    // ==================== 关键词提取与工具 ====================

    /**
     * 轻量关键词提取：英文/数字按词，中文「领域词最长匹配优先 + 2 字滑窗补充」。
     *
     * <p>纯 2 字滑窗的问题：一句「财务部上个月谁迟到了」会切出「财务/务部/部上/上个/个月/
     * 月谁」，真正有区分度的「迟到」排第 8 位被 maxKeywords=6 截掉，导致关键词路 0 命中。
     * 因此先用领域词表在整句上做最长匹配（命中「迟到」「财务部」这类真实业务词），
     * 记录其字符区间；再补滑窗时跳过被区间覆盖的碎片，避免噪声重复占位。
     *
     * <p>关键词路只是重排信号与故障兜底，不引入分词库；结果剔除 ILIKE 通配符，
     * 并至少保留 1 个关键词，避免 &lt;foreach&gt; 展开出空的 AND () 语法错误。
     */
    private List<String> extractKeywords(String question) {
        int max = positive(properties.getMaxKeywords(), 6);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        // 已被领域词覆盖的字符区间，滑窗阶段跳过，防止「务部/部上」这类碎片占位
        List<int[]> covered = new ArrayList<>();
        java.util.regex.Matcher ascii = ASCII_WORD.matcher(question);
        while (ascii.find() && result.size() < max) {
            addKeyword(result, ascii.group().toLowerCase());
        }
        // 1. 领域词最长匹配优先：长词先占位，子串词不再重复提取
        List<String> domainTerms = properties.getDomainTerms();
        if (domainTerms != null) {
            List<String> sorted = new ArrayList<>(domainTerms);
            sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
            for (String term : sorted) {
                if (result.size() >= max || !hasText(term)) {
                    break;
                }
                int from = 0;
                int at;
                while ((at = question.indexOf(term, from)) >= 0) {
                    addKeyword(result, term);
                    covered.add(new int[]{at, at + term.length()});
                    from = at + term.length();
                    if (result.size() >= max) {
                        break;
                    }
                }
            }
        }
        // 2. 连续中文片段：整段优先，再补 2 字滑窗（跳过被领域词覆盖的碎片）
        String[] segments = NON_WORD.split(question);
        for (String segment : segments) {
            if (segment == null || !segment.matches(".*\\p{IsHan}.*") || segment.length() < 2) {
                continue;
            }
            String chinese = segment.replaceAll("[^\\p{IsHan}]", "");
            if (chinese.length() >= 2) {
                if (chinese.length() <= 8) {
                    addKeyword(result, chinese);
                }
                for (int i = 0; i + 2 <= chinese.length() && result.size() < max; i++) {
                    String bigram = chinese.substring(i, i + 2);
                    if (STOP_WORDS.contains(bigram) || isCovered(covered, question, i)) {
                        continue;
                    }
                    addKeyword(result, bigram);
                }
            }
        }
        if (result.isEmpty()) {
            String fallback = ILIKE_UNSAFE.matcher(question).replaceAll("");
            if (!hasText(fallback)) {
                fallback = "schema_recall_no_keyword";
            }
            addKeyword(result, fallback.length() > 24 ? fallback.substring(0, 24) : fallback);
        }
        List<String> list = new ArrayList<>(result);
        return list.size() > max ? list.subList(0, max) : list;
    }

    /**
     * 判断中文片段中 [i, i+2) 这一滑窗是否落在已命中的领域词区间内。
     *
     * <p>滑窗基于去掉非中文字符的 chinese 串，而领域词区间基于原 question，
     * 这里按中文字符出现顺序重新定位，保证两种下标口径一致。
     */
    private static boolean isCovered(List<int[]> covered, String question, int chineseIndex) {
        if (covered.isEmpty()) {
            return false;
        }
        // 找到 chinese 第 i、i+1 两个中文字符在 question 中的原始下标（中间可能夹标点）
        int hanSeen = -1;
        int qStart = -1;
        int qEnd = -1;
        for (int p = 0; p < question.length(); p++) {
            if (isHan(question.charAt(p))) {
                hanSeen++;
                if (hanSeen == chineseIndex) {
                    qStart = p;
                } else if (hanSeen == chineseIndex + 1) {
                    qEnd = p + 1;
                    break;
                }
            }
        }
        if (qStart < 0 || qEnd < 0) {
            return false;
        }
        for (int[] range : covered) {
            if (qStart < range[1] && qEnd > range[0]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    private void addKeyword(Set<String> result, String raw) {
        if (!hasText(raw) || STOP_WORDS.contains(raw)) {
            return;
        }
        String cleaned = ILIKE_UNSAFE.matcher(raw.trim()).replaceAll("");
        if (cleaned.length() >= 2 && cleaned.length() <= 24) {
            result.add(cleaned);
        }
    }

    private void fillDatasource(SchemaContext ctx, AiDatasource ds) {
        ctx.setStructVersion(ds.getStructVersion());
        ctx.setDialect(safe(ds.getDialect()));
        ctx.setIdentifierQuote(safe(ds.getIdentifierQuote()));
        ctx.setQuoteAll(ds.getQuoteAll());
        ctx.setSqlRules(safe(ds.getSqlRules()));
    }

    private static String normalize(String question) {
        if (!hasText(question)) {
            return "";
        }
        String s = WHITESPACE.matcher(question.trim()).replaceAll(" ");
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static void appendPart(StringBuilder sb, String label, String value) {
        if (sb.length() > 0) {
            sb.append(" | ");
        }
        sb.append(label).append(": ").append(singleLine(value));
    }

    private static String singleLine(String value) {
        return WHITESPACE.matcher(safe(value)).replaceAll(" ").trim();
    }

    private static String qualified(String schema, String table) {
        return hasText(schema) ? safe(schema) + "." + safe(table) : safe(table);
    }

    private static int estimateTokens(String content) {
        return content == null ? 0 : content.length() / 4;
    }

    private static String brief(Throwable ex) {
        String msg = ex.getMessage();
        String type = ex.getClass().getSimpleName();
        if (!hasText(msg)) {
            return type;
        }
        String flat = WHITESPACE.matcher(msg).replaceAll(" ");
        return type + ": " + (flat.length() > 120 ? flat.substring(0, 120) : flat);
    }

    /** 统计某 object_type 的候选数量（1=表级，2=列级），仅用于日志观测召回饥饿 */
    private static long countByType(List<SchemaVectorHit> hits, int objectType) {
        return hits.stream()
                .filter(h -> h.getObjectType() != null && h.getObjectType() == objectType)
                .count();
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 1000000.0) / 1000000.0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** RRF 融合的中间对象 */
    private static final class Candidate {
        private final String key;
        private Long id;
        private Integer objectType;
        private Long tableId;
        private Long columnId;
        private String tableName;
        private String columnName;
        private Double distance;
        private Double keywordScore;
        private Integer vectorRank;
        private Integer keywordRank;
        private String source;
        private double score;

        private Candidate(String key) {
            this.key = key;
        }

        static Candidate of(SchemaVectorHit hit) {
            Candidate c = new Candidate(hit.key());
            c.id = hit.getId();
            c.objectType = hit.getObjectType();
            c.tableId = hit.getTableId();
            c.columnId = hit.getColumnId();
            c.tableName = hit.getTableName();
            c.columnName = hit.getColumnName();
            return c;
        }
    }

}
