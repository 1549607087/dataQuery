package cn.iocoder.boot.module.data_query.framework.knowledge.table;

import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderRespVO;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.boot.module.data_query.framework.knowledge.chunk.ChunkSplitter;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.ParsedBlock;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.ParsedDocument;
import cn.iocoder.boot.module.data_query.framework.model.ModelClient;
import cn.iocoder.boot.module.data_query.framework.model.ModelGovernanceExecutor;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocation;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocationResult;
import cn.iocoder.boot.module.data_query.service.PromptRenderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 表格 LLM 叙述服务：把 DOCX 表格的扁平文本交给轻量模型，整理成分段自然语言描述。
 *
 * <p>只在摄取阶段（MQ 消费）调用，不在问答热路径。整段逻辑对失败零容忍度的反面 ——
 * 任何异常都降级为「保留原表文本」，绝不让一张表拖垮整篇文档摄取（沿用 026/063 非阻断先例）。
 *
 * <p>输入是 DocxParser.tableText() 产出的扁平文本（行间 \n，单元格 " | " 分隔）。
 * 这里先反解为 Markdown，再按 token/行数分批送 LLM，校验通过后按段落切分并加 20% 桥接前缀，
 * 输出 blockIndex -> 多段描述的映射，交给 DocumentTextRewriter 做一对多展开。
 */
@Slf4j
@Service
public class TableNarrateService {

    public static final String SCENE_TABLE_NARRATE = "TABLE_NARRATE";

    @Resource
    private KnowledgeProperties properties;
    @Resource
    private PromptRenderService promptRenderService;
    @Resource
    private ModelGovernanceExecutor modelGovernanceExecutor;
    @Resource
    private ModelClient modelClient;

    /**
     * 对文档中所有 TABLE 块做叙述化。
     *
     * @return blockIndex -> 该表展开的 N 段描述（每段已含桥接前缀）；失败的表不放进 map（保留原文）
     */
    public Map<Integer, List<String>> narrate(ParsedDocument doc, Long tenantId, String traceId) {
        Map<Integer, List<String>> result = new HashMap<>();
        if (doc == null || doc.getBlocks() == null || doc.getBlocks().isEmpty()) {
            return result;
        }
        String rootTrace = traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId;
        for (int i = 0; i < doc.getBlocks().size(); i++) {
            ParsedBlock block = doc.getBlocks().get(i);
            if (!"TABLE".equals(block.getKind())) {
                continue;
            }
            try {
                List<String> segments = narrateOne(block, tenantId, rootTrace + "-" + i);
                if (segments != null && !segments.isEmpty()) {
                    result.put(i, segments);
                }
            } catch (Exception ex) {
                // 单表失败降级：保留原表文本，不影响整篇文档
                log.warn("[table-narrate] 表格叙述失败，保留原表文本：tenantId={}, blockIndex={}, reason={}",
                        tenantId, i, brief(ex));
            }
        }
        return result;
    }

    /** 单表叙述：反解 -> 分批 -> 逐批 LLM -> 分段 -> 桥接；失败返回 null */
    private List<String> narrateOne(ParsedBlock block, Long tenantId, String traceId) {
        List<List<String>> rows = parseFlattened(block.getText());
        if (rows.size() < 2) {
            // 不足两行（无表头或空表），叙述意义不大
            return null;
        }
        List<List<List<String>>> batches = splitRows(rows, tokenBudget(), batchRows(), maxBatches());
        if (batches == null) {
            log.warn("[table-narrate] 表格超过最大批次数，保留原表：max={}, rows={}", maxBatches(), rows.size());
            return null;
        }

        List<String> allSegments = new ArrayList<>();
        for (int b = 0; b < batches.size(); b++) {
            List<List<String>> batch = batches.get(b);
            String markdown = toMarkdown(batch);
            String narration = callLlm(markdown, block.getHeadingPath(), b + 1, batches.size(), tenantId, traceId);
            if (narration == null || narration.isBlank()) {
                return null; // 任一批失败 -> 整表降级，避免只叙述一半
            }
            // 校验：关键单元格值覆盖率 + 防膨胀
            if (!validate(batch, markdown, narration)) {
                log.warn("[table-narrate] 校验未通过，保留原表：tenantId={}, batch={}/{}",
                        tenantId, b + 1, batches.size());
                return null;
            }
            allSegments.addAll(splitIntoSegments(narration, segmentTokenBudget()));
        }
        if (allSegments.isEmpty()) {
            return null;
        }
        return applyBridges(allSegments, bridgeRatio());
    }

    // ==================== 扁平文本 -> Markdown ====================

    /** 按行 \n、列 " | " 反解 DocxParser.tableText() 的扁平文本 */
    List<List<String>> parseFlattened(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return rows;
        }
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            // " | " 是正则元字符组合，用 Pattern.quote 精确切
            String[] cells = line.split(java.util.regex.Pattern.quote(" | "), -1);
            List<String> row = new ArrayList<>(cells.length);
            for (String cell : cells) {
                row.add(cell.trim());
            }
            rows.add(row);
        }
        return rows;
    }

    /** rows -> Markdown 表格：第一行为表头，后跟分隔行 */
    String toMarkdown(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        int cols = rows.get(0).size();
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(joinEscaped(rows.get(0))).append(" |\n");
        sb.append("| ");
        for (int c = 0; c < cols; c++) {
            sb.append("---").append(c == cols - 1 ? " |\n" : " | ");
        }
        for (int r = 1; r < rows.size(); r++) {
            sb.append("| ").append(joinEscaped(rows.get(r))).append(" |\n");
        }
        return sb.toString();
    }

    private String joinEscaped(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            // 单元格内的 | 转义，避免破坏 Markdown 列结构
            sb.append(cells.get(i).replace("|", "\\|").replace("\n", " "));
        }
        return sb.toString();
    }

    /** 按 token 预算 / 最大行数分批；每批都带表头；超过 maxBatches 返回 null */
    List<List<List<String>>> splitRows(List<List<String>> rows, int tokenBudget, int batchRows, int maxBatches) {
        if (rows.size() < 2) {
            return List.of(rows);
        }
        List<List<List<String>>> batches = new ArrayList<>();
        List<List<String>> header = List.of(rows.get(0));
        List<List<String>> current = new ArrayList<>();
        int currentTokens = ChunkSplitter.estimateTokens(toMarkdown(header));
        for (int r = 1; r < rows.size(); r++) {
            int rowTokens = ChunkSplitter.estimateTokens(toMarkdown(List.of(rows.get(r)))) + 2;
            if (!current.isEmpty() && (current.size() >= batchRows || currentTokens + rowTokens > tokenBudget)) {
                List<List<String>> batch = new ArrayList<>(header);
                batch.addAll(current);
                batches.add(batch);
                current = new ArrayList<>();
                currentTokens = ChunkSplitter.estimateTokens(toMarkdown(header));
            }
            current.add(rows.get(r));
            currentTokens += rowTokens;
        }
        if (!current.isEmpty()) {
            List<List<String>> batch = new ArrayList<>(header);
            batch.addAll(current);
            batches.add(batch);
        }
        return batches.size() > maxBatches ? null : batches;
    }

    // ==================== LLM 调用 ====================

    private String callLlm(String markdown, String headingPath, int batchNo, int batchTotal,
                           Long tenantId, String traceId) {
        Map<String, String> variables = new HashMap<>();
        variables.put("table_markdown", markdown.length() > 12000 ? markdown.substring(0, 12000) : markdown);
        variables.put("heading_path", headingPath == null ? "" : headingPath);
        variables.put("batch_no", String.valueOf(batchNo));
        variables.put("batch_total", String.valueOf(batchTotal));

        PromptRenderReqVO reqVO = new PromptRenderReqVO();
        reqVO.setSceneCode(SCENE_TABLE_NARRATE);
        reqVO.setVariables(variables);
        reqVO.setRecallSchema(false);
        PromptRenderRespVO prompt = promptRenderService.render(reqVO);

        ModelInvocation invocation = new ModelInvocation();
        invocation.setTraceId(traceId);
        invocation.setSceneCode(SCENE_TABLE_NARRATE);
        invocation.setPrompt(prompt.getContent());
        invocation.setPromptId(prompt.getPromptId());
        invocation.setTenantId(tenantId); // 显式固化，异步线程不丢租户
        invocation.setTimeoutMs(tableNarrateTimeoutMs());

        ModelInvocationResult result = modelGovernanceExecutor.execute(invocation, modelClient);
        if (result == null || Boolean.TRUE.equals(result.getEcho())) {
            // EchoModelClient 的回声不是业务结果
            throw new IllegalStateException("表格叙述模型未接入真实实现（echo）或未返回结果");
        }
        return result.getContent();
    }

    // ==================== 校验 / 分段 / 桥接 ====================

    /**
     * 防幻觉校验：
     * 1. 关键单元格值（纯数字/金额/日期/短编号）必须按比例出现在叙述文本里；
     * 2. 叙述 token 不得超过输入 Markdown token 的 2 倍（防编造长篇）。
     */
    boolean validate(List<List<String>> batch, String markdown, String narration) {
        int inputTokens = ChunkSplitter.estimateTokens(markdown);
        if (ChunkSplitter.estimateTokens(narration) > inputTokens * 2L) {
            return false;
        }
        String normalized = narration.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        List<String> keyCells = collectKeyCells(batch);
        if (keyCells.isEmpty()) {
            return true;
        }
        int hit = 0;
        for (String cell : keyCells) {
            String c = cell.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (!c.isEmpty() && normalized.contains(c)) {
                hit++;
            }
        }
        double coverage = (double) hit / keyCells.size();
        return coverage >= minCellCoverage();
    }

    /** 关键单元格：非空、去表头后，纯数字/金额/日期/编号 或长度<=16 的短值 */
    private List<String> collectKeyCells(List<List<String>> batch) {
        Set<String> seen = new LinkedHashSet<>();
        for (int r = 1; r < batch.size(); r++) { // 跳过表头行
            for (String cell : batch.get(r)) {
                String v = cell == null ? "" : cell.trim();
                if (v.isEmpty() || v.length() > 40) {
                    continue;
                }
                boolean numericOrDate = v.matches("[\\d.,:：/\\-年月日%元¥￥\\s]+");
                boolean shortValue = v.length() <= 16;
                if (numericOrDate || shortValue) {
                    seen.add(v);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** 按空行分段，超长段再按句号/分号切，保证每段 <= budget token */
    List<String> splitIntoSegments(String narration, int budget) {
        List<String> segments = new ArrayList<>();
        for (String paragraph : narration.split("\\n\\s*\\n")) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (ChunkSplitter.estimateTokens(p) <= budget) {
                segments.add(p);
            } else {
                segments.addAll(splitLongParagraph(p, budget));
            }
        }
        return segments;
    }

    /** 超长段落按句号/分号/换行切句，累加到接近 budget 后另起一段 */
    private List<String> splitLongParagraph(String p, int budget) {
        List<String> out = new ArrayList<>();
        String[] sentences = p.split("(?<=[。；;！!？?\\n])");
        StringBuilder cur = new StringBuilder();
        int curTokens = 0;
        for (String s : sentences) {
            int t = ChunkSplitter.estimateTokens(s);
            if (cur.length() > 0 && curTokens + t > budget) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                curTokens = 0;
            }
            cur.append(s);
            curTokens += t;
        }
        if (cur.length() > 0) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    /** 相邻段桥接：后一段开头拼上一段结尾 bridgeRatio 比例的文本（token 口径） */
    List<String> applyBridges(List<String> segments, double ratio) {
        if (segments == null || segments.size() <= 1 || ratio <= 0) {
            return segments;
        }
        List<String> result = new ArrayList<>(segments.size());
        result.add(segments.get(0));
        for (int i = 1; i < segments.size(); i++) {
            String prev = segments.get(i - 1);
            int bridgeTokens = Math.max(1, (int) Math.round(ChunkSplitter.estimateTokens(prev) * ratio));
            String tail = tailByTokens(prev, bridgeTokens);
            result.add(tail + "\n\n" + segments.get(i));
        }
        return result;
    }

    /** 取字符串末尾不超过 maxTokens 的后缀 */
    private String tailByTokens(String text, int maxTokens) {
        int cost = 0;
        int from = text.length();
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            cost += (c >= 0x4E00 && c <= 0x9FFF) ? 1 : 1; // 统一按 1 计，偏保守（桥接略长无妨）
            if (cost > maxTokens) {
                from = i + 1;
                break;
            }
        }
        return text.substring(from);
    }

    // ==================== 配置读取（带默认值，便于单测） ====================

    private int tokenBudget() {
        Integer v = properties.getTableNarrateTokenBudget();
        return v != null && v > 0 ? v : 2000;
    }

    private int batchRows() {
        Integer v = properties.getTableNarrateBatchRows();
        return v != null && v > 0 ? v : 40;
    }

    private int maxBatches() {
        Integer v = properties.getTableNarrateMaxBatchesPerTable();
        return v != null && v > 0 ? v : 10;
    }

    private int tableNarrateTimeoutMs() {
        Integer v = properties.getTableNarrateTimeoutMs();
        return v != null && v > 0 ? v : 30000;
    }

    private double bridgeRatio() {
        Double v = properties.getTableNarrateBridgeRatio();
        return v != null && v >= 0 && v < 1 ? v : 0.2;
    }

    private double minCellCoverage() {
        Double v = properties.getTableNarrateMinCellCoverage();
        return v != null && v >= 0 && v <= 1 ? v : 0.8;
    }

    private int segmentTokenBudget() {
        Integer chunkSize = properties.getChunkSize();
        Double ratio = properties.getTableNarrateSegmentRatio();
        int cs = chunkSize != null && chunkSize > 0 ? chunkSize : 500;
        double r = ratio != null && ratio > 0 && ratio < 1 ? ratio : 0.5;
        return Math.max(1, (int) (cs * r));
    }

    private static String brief(Exception ex) {
        String m = ex.getMessage();
        return m == null ? ex.getClass().getSimpleName() : m.substring(0, Math.min(200, m.length()));
    }
}
