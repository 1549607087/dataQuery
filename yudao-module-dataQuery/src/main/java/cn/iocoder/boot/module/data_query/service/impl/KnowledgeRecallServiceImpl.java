package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.boot.module.data_query.framework.knowledge.chunk.ChunkSplitter;
import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeChunkHit;
import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeRecallResult;
import cn.iocoder.boot.module.data_query.framework.knowledge.rerank.KnowledgeRerankProvider;
import cn.iocoder.boot.module.data_query.framework.knowledge.rerank.RerankResult;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaEmbeddingProvider;
import cn.iocoder.boot.module.data_query.mapper.AiKnowledgeChunkMapper;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
import cn.iocoder.boot.module.data_query.service.KnowledgeRecallService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_DISABLED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId;

/**
 * 知识库切片混合召回实现
 *
 * <p>召回策略与 Schema 召回（{@link SchemaRecallServiceImpl}）同源，但过滤维度换成
 * (tenant_id, base_id)，且只召回 sync_status=1 的已构建切片：
 * <ul>
 *   <li>向量路：Embedding 可用 + 模型代号一致时执行，精确余弦距离升序；</li>
 *   <li>关键词路：正则 + CASE 打分，<b>按需触发</b> —— 向量不可用时兜底，向量可用时仅在召回
 *       不自信（命中条数少或最近距离大）时补跑，避免每轮问答都付一遍无索引顺序扫描的成本；</li>
 *   <li>RRF 融合：score = Σ weight/(K+rank)，按切片主键归并；</li>
 *   <li>先裁剪后编号：只有真正保留的条目才占 [n]，保证编号连续、与 references 一一对应。</li>
 * </ul>
 * 任一异常只降级、不中断；两路皆空返回空结果（由编排器转「未找到依据」话术）。
 */
@Slf4j
@Service
public class KnowledgeRecallServiceImpl implements KnowledgeRecallService {

    /** 中文停用词：只用于关键词路，避免「查询 / 统计 / 的」之类噪声撑满 ILIKE 分支 */
    private static final Set<String> STOP_WORDS = Set.of(
            "查询", "统计", "请问", "帮我", "一下", "多少", "汇总", "显示", "列出", "计算",
            "数据", "报表", "情况", "所有", "每个", "各个", "分别", "按照", "根据", "以及",
            "时间", "日期", "今年", "去年", "本月", "上月", "最近", "趋势", "对比", "排行");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9_]+");
    private static final Pattern ASCII_WORD = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{1,}");
    /** 关键词清洗：空白、引号会破坏 SQL 拼接与正则，通配符/反斜杠无检索意义 */
    private static final Pattern KEYWORD_UNSAFE = Pattern.compile("[%_\\\\'\"\\s]+");
    /** PG ARE 元字符，逐字符转义后关键词才按字面量匹配（不是所有 kw 都干净） */
    private static final String REGEX_META = "\\^$.|?*+()[]{}\\";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final int KNOWLEDGE_BASE_STATUS_NORMAL = 0;

    /** context 去重：匹配长度低于此值视为巧合（如通用公文抬头），不去重 */
    private static final int DEDUPE_MIN_CHARS = 16;
    /** context 去重：单条最多砍掉 60%，防止把不相邻的相似切片误判为重叠 */
    private static final double DEDUPE_MAX_RATIO = 0.6;
    /** 精排只送每片开头的固定 token 预算：中文约 256 字，英文约 1024 字符 */
    private static final int RERANK_DOC_PREVIEW_TOKENS = 256;

    @Resource
    private AiKnowledgeChunkMapper chunkMapper;
    @Resource
    private SchemaEmbeddingProvider embeddingProvider;
    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;
    @Resource
    private KnowledgeProperties properties;
    @Resource
    private KnowledgeRerankProvider rerankProvider;

    @Override
    public KnowledgeRecallResult recall(Long baseId, String question, Integer topK, Integer version) {
        long started = System.currentTimeMillis();
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        // 版本水位非法（负数/0）按「默认最新」降级，与 DOC_RECALL_VERSION_INVALID(122) 的降级范式一致
        Integer effectiveVersion = version != null && version > 0 ? version : null;
        if (version != null && (version <= 0)) {
            log.warn("[knowledge-recall] 回溯版本非法，降级为当前生效版本, baseId={}, version={}", baseId, version);
        }

        AiKnowledgeBase base = knowledgeBaseService.getKnowledgeBase(baseId);
        if (base == null) {
            throw exception(KNOWLEDGE_BASE_NOT_EXISTS);
        }
        if (base.getStatus() == null || base.getStatus() != KNOWLEDGE_BASE_STATUS_NORMAL) {
            throw exception(KNOWLEDGE_BASE_DISABLED);
        }

        String normalized = normalize(question);
        KnowledgeRecallResult result = new KnowledgeRecallResult();
        result.setBaseId(baseId);
        result.setQuestion(normalized);
        result.setDegraded(false);
        result.setTruncated(false);
        result.setVectorCandidateCount(0);
        result.setKeywordCandidateCount(0);
        result.setKeywordSkipped(false);

        List<String> keywords = extractKeywords(normalized);
        List<KnowledgeChunkHit> vectorHits = new ArrayList<>();
        List<KnowledgeChunkHit> keywordHits = new ArrayList<>();

        // ---- 1. 向量路：Embedding 可用且模型代号与知识库一致才执行 ----
        String modelCode = null;
        boolean vectorUsable = false;
        if (!embeddingProvider.isAvailable(tenantId)) {
            result.setDegraded(true);
            result.setDegradeReason("当前租户未配置可用的 Embedding 模型，已降级关键词召回");
        } else {
            modelCode = embeddingProvider.currentModelCode(tenantId);
            String baseModelCode = base.getEmbeddingModelCode();
            if (StringUtils.hasText(baseModelCode) && StringUtils.hasText(modelCode)
                    && !baseModelCode.equals(modelCode)) {
                // 知识库向量由旧模型构建：不混用，避免召回质量不可控
                result.setDegraded(true);
                result.setDegradeReason("知识库向量模型与当前 Embedding 模型不一致，请重建知识库向量；已降级关键词召回");
            } else if (!StringUtils.hasText(modelCode)) {
                result.setDegraded(true);
                result.setDegradeReason("当前租户 Embedding 模型代号为空，已降级关键词召回");
            } else {
                vectorUsable = true;
            }
        }
        result.setEmbeddingModelCode(modelCode);

        if (vectorUsable) {
            try {
                List<String> vectors = embeddingProvider.embed(tenantId, List.of(normalized));
                if (vectors != null && !vectors.isEmpty() && StringUtils.hasText(vectors.get(0))) {
                    vectorHits = chunkMapper.searchByVector(tenantId, baseId, modelCode, vectors.get(0),
                            effectiveVersion, positive(properties.getVectorCandidates(), 20));
                }
            } catch (Exception ex) {
                log.warn("[knowledge-recall] 向量路失败，降级关键词, baseId={}, reason={}", baseId, brief(ex));
                result.setDegraded(true);
                result.setDegradeReason("向量召回失败，已降级关键词召回：" + brief(ex));
            }
        }
        if (vectorHits == null) {
            vectorHits = new ArrayList<>();
        }

        // ---- 2. 关键词路：按需触发 ----
        // 关键词路对着 content 做无索引扫描（无 pg_trgm），向量已召回得足够好时不必每轮都付这份成本
        boolean keywordEnabled = !keywords.isEmpty() && needKeywordSupplement(vectorHits, vectorUsable);
        if (keywordEnabled) {
            try {
                List<String> patterns = escapeAll(keywords);
                keywordHits = chunkMapper.searchByKeyword(tenantId, baseId, patterns,
                        String.join("|", patterns), effectiveVersion,
                        positive(properties.getKeywordCandidates(), 20));
            } catch (Exception ex) {
                log.warn("[knowledge-recall] 关键词路失败, baseId={}, reason={}", baseId, brief(ex));
            }
        } else if (!keywords.isEmpty()) {
            // 未跑关键词路有两种成因，reason 必须区分清楚，否则排查时会误判成「向量自信」
            String reason = !vectorUsable
                    ? "向量不可用且未开启关键词兜底（keywordFallbackEnabled=false），关键词路按配置关闭"
                    : describeSkip(vectorHits);
            result.setKeywordSkipped(true);
            result.setKeywordSkipReason(reason);
            log.debug("[knowledge-recall] 跳过关键词路, baseId={}, {}", baseId, reason);
        }
        if (keywordHits == null) {
            keywordHits = new ArrayList<>();
        }
        result.setVectorCandidateCount(vectorHits.size());
        result.setKeywordCandidateCount(keywordHits.size());

        // ---- 3. RRF 融合 ----
        List<KnowledgeChunkHit> ranked = fuse(vectorHits, keywordHits);

        // ---- 4. 裁剪 → 重排 → 重新编号（三步拆分，唯一编号点在 Step C）----
        int limit = topK == null || topK <= 0 ? positive(properties.getTopK(), 6) : topK;
        int budget = positive(properties.getContextTokenBudget(), 6000);

        // Step A：只裁出 Top-K 候选集合（不编号、不拼 context，保持"首条必留"语义）
        List<KnowledgeChunkHit> candidates = trimCandidates(ranked, limit, budget, result);

        // Step B：仅对这批候选精排（不扩大候选池）；任何失败都退回 RRF 原序
        RerankOutcome outcome = applyRerank(tenantId, normalized, candidates);
        List<KnowledgeChunkHit> kept = outcome.ordered;

        // Step C：统一重新编号 + 重建 context，保证 items[i].refNo == i+1 恒等
        StringBuilder context = new StringBuilder();
        int used = renumberAndBuild(kept, outcome.scores, context, budget, result);

        result.setItems(kept);
        result.setKeptCount(kept.size());
        result.setContextText(context.toString().trim());
        result.setTokenEstimate(used);
        result.setMode(resolveMode(vectorHits, keywordHits, keywordEnabled, vectorUsable));
        result.setLatencyMs((int) (System.currentTimeMillis() - started));
        // Rerank 观测字段（无论是否执行都写入，便于 retrieve-preview 排查）
        result.setRerankApplied(outcome.applied);
        result.setRerankSkipReason(outcome.skipReason);
        result.setRerankModelCode(outcome.modelCode);
        result.setRerankLatencyMs(outcome.latencyMs);
        return result;
    }

    /**
     * Step A：从 RRF 全量 ranked 中裁出 Top-K 候选。
     *
     * <p>只选集合、不编号、不拼 block（编号统一放到 Step C，避免 rerank 重排后引用错位）。
     * Token 预算校验沿用"首条必留 + 超出即停"口径，与原实现逐字节一致。
     */
    private List<KnowledgeChunkHit> trimCandidates(List<KnowledgeChunkHit> ranked, int limit,
                                                   int budget, KnowledgeRecallResult result) {
        List<KnowledgeChunkHit> out = new ArrayList<>();
        int used = 0;
        for (KnowledgeChunkHit hit : ranked) {
            if (out.size() >= limit) {
                result.setTruncated(true);
                break;
            }
            int cost = estimateTokens(buildBlock(out.size() + 1, hit));
            if (!out.isEmpty() && used + cost > budget) {
                result.setTruncated(true);
                break;
            }
            out.add(hit);
            used += cost;
        }
        return out;
    }

    /**
     * Step B：仅对 Top-K 候选做精排。
     *
     * <p><b>降级边界</b>：整个方法吞掉一切异常（含 ServiceException），只回写 skipReason，
     * 绝不把 rerank 故障传到问答主链路。分数非法（数量不符 / NaN / Infinity）同样退回原序。
     * 同分按原 index 排列（稳定排序），保证全部同分时不改变 RRF 原序。
     */
    private RerankOutcome applyRerank(Long tenantId, String query, List<KnowledgeChunkHit> candidates) {
        RerankOutcome outcome = new RerankOutcome();
        outcome.ordered = candidates;
        outcome.applied = false;
        if (candidates.isEmpty()) {
            outcome.skipReason = "rerank 候选为空";
            return outcome;
        }
        if (rerankProvider == null || !rerankProvider.isAvailable(tenantId)) {
            outcome.skipReason = "当前租户未配置可用的 RERANK 模型绑定(model_type=3)";
            return outcome;
        }
        long began = System.currentTimeMillis();
        try {
            // 只送每片开头：精排判的是"这段跟问题相关吗"，开头几百字足够；
            // 整片送进去等于把同一批内容再付一遍输入费。分数仍按候选原序回填，编号不受影响。
            List<String> documents = candidates.stream()
                    .map(KnowledgeChunkHit::getContent)
                    .map(KnowledgeRecallServiceImpl::rerankPreview)
                    .toList();
            RerankResult rr = rerankProvider.rerank(tenantId, query, documents);
            if (rr == null || rr.getScores() == null || rr.getScores().length != documents.size()) {
                outcome.skipReason = "rerank 返回分数数量与候选数不符，已按 RRF 原序降级";
                outcome.latencyMs = (int) (System.currentTimeMillis() - began);
                return outcome;
            }
            double[] scores = rr.getScores();
            for (double score : scores) {
                if (!Double.isFinite(score)) {
                    outcome.skipReason = "rerank 返回非有限分数，已按 RRF 原序降级";
                    outcome.latencyMs = (int) (System.currentTimeMillis() - began);
                    return outcome;
                }
            }
            // 稳定排序：分数降序；同分按原 index（保证全同分 = RRF 原序）
            List<Integer> order = new ArrayList<>(candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                order.add(i);
            }
            order.sort((i, j) -> {
                int cmp = Double.compare(scores[j], scores[i]);
                return cmp != 0 ? cmp : Integer.compare(i, j);
            });
            List<KnowledgeChunkHit> ordered = new ArrayList<>(candidates.size());
            double[] orderedScores = new double[candidates.size()];
            for (int i = 0; i < order.size(); i++) {
                int src = order.get(i);
                ordered.add(candidates.get(src));
                orderedScores[i] = scores[src];
            }
            outcome.ordered = ordered;
            outcome.scores = orderedScores;
            outcome.applied = true;
            outcome.modelCode = rr.getModelCode();
        } catch (Exception ex) {
            // 异常黑洞：rerank 任何失败都降级，绝不外溢到问答链路
            outcome.ordered = candidates;
            outcome.scores = null;
            outcome.skipReason = "rerank 失败已降级：" + brief(ex);
            log.warn("[knowledge-recall] rerank 失败已降级 baseId={} reason={}",
                    candidates.isEmpty() ? null : candidates.get(0).getBaseId(), brief(ex));
        }
        outcome.latencyMs = (int) (System.currentTimeMillis() - began);
        return outcome;
    }

    /**
     * Step C：重排后统一编号 + 重建 context。
     *
     * <p>buildBlock 函数体不改，只是调用时机从裁剪循环移到此处；refNo 严格按 items 下标分配，
     * 使 CitationValidator 的 hits.get(n-1) 能精确映射回答中的 [n]。
     */
    private int renumberAndBuild(List<KnowledgeChunkHit> kept, double[] scores,
                                 StringBuilder context, int budget, KnowledgeRecallResult result) {
        int used = 0;
        KnowledgeChunkHit previous = null;
        for (int i = 0; i < kept.size(); i++) {
            KnowledgeChunkHit hit = kept.get(i);
            int refNo = i + 1;
            hit.setRefNo(refNo);
            hit.setRerankScore(scores == null ? null : scores[i]);
            // 去重只作用于"写进 context 的文本"：hit 本身、列表长度与 refNo 一律不动，
            // 保证 items[i].refNo == i+1，回答里的 [n] 仍能精确映射到同一条切片。
            String block = buildBlock(refNo, hit, dedupeAgainstPrevious(hit, previous));
            int cost = estimateTokens(block);
            if (i > 0 && used + cost > budget) {
                result.setTruncated(true);
                break;
            }
            context.append(block).append("\n\n");
            used += cost;
            previous = hit;
        }
        return used;
    }

    /**
     * 拼接去重：同一文档的相邻切片因 overlap 会在开头重复上一条的结尾。
     *
     * <p>只认"当前开头 == 上一条结尾"的精确长匹配（至少 16 字），且最多砍掉当前片的 60%。
     * 上一条用<b>原始内容</b>比对（重叠是相对原文计算的）；跨文档、跨版本一律不碰。
     * 返回空时保留原文，避免给模型一个空资料块。
     */
    private static String dedupeAgainstPrevious(KnowledgeChunkHit current, KnowledgeChunkHit previous) {
        String content = safe(current.getContent());
        if (previous == null || !sameSource(current, previous)) {
            return content;
        }
        String prior = safe(previous.getContent());
        if (prior.isEmpty() || content.isEmpty()) {
            return content;
        }
        // 先找完整的最长匹配（重叠是相对原文精确计算的），再用 60% 上限兜底：
        // 若先把上限卡到 60% 再找，遇到超长重叠会退化成"砍半个句子"，留下半截话。
        int upper = Math.min(prior.length(), content.length());
        int matched = 0;
        for (int k = upper; k >= DEDUPE_MIN_CHARS; k--) {
            if (prior.regionMatches(prior.length() - k, content, 0, k)) {
                matched = k;
                break;
            }
        }
        if (matched == 0 || matched > content.length() * DEDUPE_MAX_RATIO) {
            return content;
        }
        String trimmed = content.substring(matched).stripLeading();
        return trimmed.isEmpty() ? content : trimmed;
    }

    /** 同文档判定：优先 documentId，缺失时退回 docKey；版本不一致一律不去重 */
    private static boolean sameSource(KnowledgeChunkHit a, KnowledgeChunkHit b) {
        if (a.getVersion() != null && b.getVersion() != null
                && !a.getVersion().equals(b.getVersion())) {
            return false;
        }
        if (a.getDocumentId() != null && b.getDocumentId() != null) {
            return a.getDocumentId().equals(b.getDocumentId());
        }
        String aKey = a.getDocKey();
        String bKey = b.getDocKey();
        return StringUtils.hasText(aKey) && aKey.equals(bKey);
    }

    /** 精排入参截断：按 token 预算取开头，中英文分开计数 */
    private static String rerankPreview(String content) {
        return ChunkSplitter.previewByTokenBudget(content, RERANK_DOC_PREVIEW_TOKENS);
    }

    /** 一次精排决策的结果载体 */
    private static final class RerankOutcome {
        /** 最终顺序（精排成功为重排后列表，失败/跳过了 RRF 原序） */
        private List<KnowledgeChunkHit> ordered = new ArrayList<>();
        /** 与 ordered 同序的分数；未精排为 null */
        private double[] scores;
        private boolean applied;
        private String skipReason;
        private String modelCode;
        private int latencyMs;
    }

    // ==================== 关键词路：触发判定与入参转义 ====================

    /**
     * 是否需要跑关键词路。
     *
     * <p>两路都跑才有混合召回的价值，但关键词路是无索引扫描、成本不低，因此只在
     * 「向量没得可用」或「向量召回不自信」时补跑：
     * <ol>
     *   <li>向量不可用 → 沿用 {@code keywordFallbackEnabled} 兜底开关；</li>
     *   <li>向量零命中 / 命中数低于阈值 / 最近距离大于阈值 → 视为不自信，补跑。</li>
     * </ol>
     */
    private boolean needKeywordSupplement(List<KnowledgeChunkHit> vectorHits, boolean vectorUsable) {
        if (!vectorUsable) {
            return Boolean.TRUE.equals(properties.getKeywordFallbackEnabled());
        }
        if (vectorHits.isEmpty()
                || vectorHits.size() < positive(properties.getKeywordSupplementMinHits(), 3)) {
            return true;
        }
        Double best = bestDistance(vectorHits);
        return best == null || best > safeDouble(properties.getKeywordSupplementMaxDistance(), 0.55);
    }

    private static String describeSkip(List<KnowledgeChunkHit> vectorHits) {
        Double best = bestDistance(vectorHits);
        return "向量召回自信，跳过关键词路：候选 " + (vectorHits == null ? 0 : vectorHits.size())
                + " 条，最近距离 " + (best == null ? "-" : String.format(Locale.ROOT, "%.3f", best));
    }

    /** 取向量路最小距离：不假设上游一定按 distance 升序，避免换实现后判定失真 */
    private static Double bestDistance(List<KnowledgeChunkHit> vectorHits) {
        if (vectorHits == null) {
            return null;
        }
        Double best = null;
        for (KnowledgeChunkHit hit : vectorHits) {
            Double distance = hit == null ? null : hit.getDistance();
            if (distance != null && (best == null || distance < best)) {
                best = distance;
            }
        }
        return best;
    }

    private static List<String> escapeAll(List<String> keywords) {
        List<String> patterns = new ArrayList<>(keywords.size());
        for (String keyword : keywords) {
            patterns.add(escapeRegex(keyword));
        }
        return patterns;
    }

    /** 逐字符转义：关键词只按字面量参与 PG 正则，避免 . * + 等被当成元字符 */
    private static String escapeRegex(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (REGEX_META.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ==================== RRF ====================

    /** 两路候选按 score += weight/(K+rank) 融合；按切片主键归并并标注命中来源 */
    private List<KnowledgeChunkHit> fuse(List<KnowledgeChunkHit> vectorHits, List<KnowledgeChunkHit> keywordHits) {
        Map<String, KnowledgeChunkHit> merged = new LinkedHashMap<>();
        int k = positive(properties.getRrfK(), 60);
        double vw = safeDouble(properties.getVectorWeight(), 1.0);
        double kw = safeDouble(properties.getKeywordWeight(), 1.0);

        for (int i = 0; i < vectorHits.size(); i++) {
            KnowledgeChunkHit h = vectorHits.get(i);
            KnowledgeChunkHit c = merged.computeIfAbsent(h.key(), key -> h);
            c.setVectorRank(i + 1);
            c.setDistance(h.getDistance());
            c.setRrfScore(safeDouble(c.getRrfScore(), 0.0) + vw / (k + (double) (i + 1)));
            c.setSource(KnowledgeChunkHitSource.VECTOR);
        }
        for (int i = 0; i < keywordHits.size(); i++) {
            KnowledgeChunkHit h = keywordHits.get(i);
            KnowledgeChunkHit c = merged.computeIfAbsent(h.key(), key -> h);
            c.setKeywordRank(i + 1);
            c.setKeywordScore(h.getKeywordScore());
            c.setRrfScore(safeDouble(c.getRrfScore(), 0.0) + kw / (k + (double) (i + 1)));
            c.setSource(c.getVectorRank() == null ? KnowledgeChunkHitSource.KEYWORD : KnowledgeChunkHitSource.BOTH);
        }

        List<KnowledgeChunkHit> ranked = new ArrayList<>(merged.values());
        ranked.sort((a, b) -> {
            int cmp = Double.compare(safeDouble(b.getRrfScore(), 0.0), safeDouble(a.getRrfScore(), 0.0));
            if (cmp != 0) {
                return cmp;
            }
            // 同分时距离升序（null 排最后），再按 id 升序保证稳定
            double da = a.getDistance() == null ? Double.MAX_VALUE : a.getDistance();
            double db = b.getDistance() == null ? Double.MAX_VALUE : b.getDistance();
            cmp = Double.compare(da, db);
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId());
        });
        return ranked;
    }

    // ==================== 上下文块 ====================

    private static String buildBlock(int refNo, KnowledgeChunkHit hit) {
        return buildBlock(refNo, hit, safe(hit.getContent()));
    }

    /** 同 buildBlock，但正文由调用方给定（拼接去重后传入） */
    private static String buildBlock(int refNo, KnowledgeChunkHit hit, String content) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("【资料 ").append(refNo).append("】");
        if (StringUtils.hasText(hit.getFileName())) {
            sb.append(hit.getFileName());
        } else {
            sb.append("未命名文档");
        }
        // 版本标识：让模型在生成时也能看到该结论来自哪一版（v 前缀，缺版本时不显示）
        if (hit.getVersion() != null && hit.getVersion() > 0) {
            sb.append("（v").append(hit.getVersion()).append("）");
        }
        if (hit.getPageNo() != null && hit.getPageNo() > 0) {
            sb.append(" | 第 ").append(hit.getPageNo()).append(" 页");
        }
        if (StringUtils.hasText(hit.getHeadingPath())) {
            sb.append(" | ").append(hit.getHeadingPath());
        }
        sb.append('\n');
        sb.append(content);
        return sb.toString();
    }

    // ==================== 关键词提取（照抄 SchemaRecallServiceImpl 口径） ====================

    private List<String> extractKeywords(String question) {
        int max = positive(properties.getMaxKeywords(), 6);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        java.util.regex.Matcher ascii = ASCII_WORD.matcher(question);
        while (ascii.find() && result.size() < max) {
            addKeyword(result, ascii.group().toLowerCase());
        }
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
                    if (!STOP_WORDS.contains(bigram)) {
                        addKeyword(result, bigram);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    private void addKeyword(Set<String> result, String raw) {
        if (!StringUtils.hasText(raw) || STOP_WORDS.contains(raw)) {
            return;
        }
        String cleaned = KEYWORD_UNSAFE.matcher(raw.trim()).replaceAll("");
        if (cleaned.length() >= 2 && cleaned.length() <= 24) {
            result.add(cleaned);
        }
    }

    // ==================== 工具 ====================

    private static String resolveMode(List<KnowledgeChunkHit> vectorHits, List<KnowledgeChunkHit> keywordHits,
                                      boolean keywordEnabled, boolean vectorUsable) {
        if (vectorHits.isEmpty() && keywordHits.isEmpty()) {
            return KnowledgeRecallResult.MODE_EMPTY;
        }
        if (!vectorUsable) {
            return KnowledgeRecallResult.MODE_KEYWORD_DEGRADED;
        }
        return keywordEnabled && !keywordHits.isEmpty()
                ? KnowledgeRecallResult.MODE_HYBRID
                : KnowledgeRecallResult.MODE_VECTOR;
    }

    private static String normalize(String question) {
        return question == null ? "" : WHITESPACE.matcher(question.trim()).replaceAll(" ");
    }

    /**
     * 近似 token 数：统一委托切分器的口径（中文一字≈1 token，其余 4 字符≈1 token）。
     *
     * <p>原实现用 {@code content.length() / 4} 对中文严重低估 —— 4 个汉字只算 1 token，
     * 导致 {@code contextTokenBudget=6000} 实际放行约 24000 汉字，硬预算形同虚设：
     * 一旦调大 topK / chunkSize 就会无声撑爆 prompt。与切分器统一后， budget 才是真闸门。
     * 当前 topK=4 × chunkSize=500 ≈ 2000 token，正常配置下不会触发裁剪，行为不变。
     */
    private static int estimateTokens(String content) {
        return ChunkSplitter.estimateTokens(content);
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static double safeDouble(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String brief(Throwable ex) {
        String msg = ex.getMessage();
        String type = ex.getClass().getSimpleName();
        if (!StringUtils.hasText(msg)) {
            return type;
        }
        String flat = WHITESPACE.matcher(msg).replaceAll(" ");
        return type + ": " + (flat.length() > 120 ? flat.substring(0, 120) : flat);
    }

    /** 命中来源常量，避免魔法字符串散落 */
    private static final class KnowledgeChunkHitSource {
        private static final String VECTOR = "VECTOR";
        private static final String KEYWORD = "KEYWORD";
        private static final String BOTH = "BOTH";
    }
}
