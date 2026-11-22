package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeChunkHit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式引用门控（回退窗口方案）。
 *
 * <p><b>要解决的问题</b>：同步链路在 S5 统一扫描全文、删掉越界编号后再返回，用户永远看不到非法引用。
 * 流式下文本一旦发出就无法回收，如果照搬「先发后校验」，模型编造的 [9] 会直接出现在用户眼前 ——
 * 那等于打开幻觉引用出口，是安全红线，不能接受。
 *
 * <p><b>为什么不用「只校验 references、正文不动」</b>：正文里留着不存在的 [9]，而右侧引用列表只有 [1]，
 * 用户点不动、对不上，比删掉更糟。
 *
 * <p><b>做法</b>：只扣住「可能是引用标记的尾巴」，其余字符立刻放行。
 * <pre>
 *   holdback = ""                       // 已收到但尚未下发的尾巴
 *   feed(delta):
 *       buf = holdback + delta
 *       扫描 buf：完整的标记 → 合法则归一为 [n] 并记账，越界则删除；
 *                 未闭合且尾部形如「[ + 0~3 位数字」→ 扣住，等下一段；
 *                 其它情况（[2024] / [abc / 空括号）→ 原样放行
 *   flush(): 放行 holdback（未闭合标记原样输出，绝不吞正文）
 * </pre>
 *
 * <p><b>对首字延迟的影响为零</b>：正常中文正文不含 {@code [} / {@code 【} / {@code ［}，
 * 门控不会扣住任何字符；只有真的遇到起始符才开始等最多 4 个字符。持有窗口上限
 * = 起始符 + 3 位数字，加闭合符 5 个字符。
 *
 * <p><b>与同步链路的已知差异</b>：删除越界编号时只吞掉紧跟的一个空格，不做全量 tidy()。
 * 落库必须用本门控的输出（{@link #answer()}），<b>不能</b>事后再跑一次
 * {@link CitationValidator#validate} 重新清洗 —— 否则会出现「刷新后答案和刚才看到的不一样」。
 * 代价是可能残留极少量多余空格，这个差异比重算文本导致的不一致更可接受。
 */
public class StreamingCitationGate {

    /** 一次标记解析最多向后看几个字符（[ + 3 位数字 + ] 再留 3 个容错空位） */
    private static final int MAX_MARKER_SPAN = 8;

    /** 与 {@link CitationValidator} 保持一致：1~3 位数字，4 位一律当正文（[2024] 是年份） */
    private static final int MAX_REF_DIGITS = 3;

    /** 本轮进入上下文的切片，索引 i 对应 [i+1] */
    private final List<KnowledgeChunkHit> hits;
    private final int itemCount;
    /** 正文硬上限（max-answer-chars）；<=0 表示不限 */
    private final int maxChars;

    /** 已收到但尚未下发的尾巴 */
    private String holdback = "";
    /** 实际下发过的正文，落库与 done 事件都以它为准 */
    private final StringBuilder emitted = new StringBuilder();
    /** 被引用的切片（按首次出现顺序去重） */
    private final Map<Integer, KnowledgeReference> references = new LinkedHashMap<>();

    private int droppedCount;
    private boolean truncated;

    public StreamingCitationGate(List<KnowledgeChunkHit> hits, int maxChars) {
        this.hits = hits == null ? List.of() : hits;
        this.itemCount = this.hits.size();
        this.maxChars = maxChars <= 0 ? Integer.MAX_VALUE : maxChars;
    }

    /**
     * 喂入一段增量，返回「本次允许下发」的文本（可能为空串）。
     *
     * @param delta 模型新增量，允许为 null / 空
     */
    public String feed(String delta) {
        if (delta == null || delta.isEmpty() || truncated) {
            return "";
        }
        String buf = holdback + delta;
        holdback = "";

        StringBuilder out = new StringBuilder(buf.length());
        int i = 0;
        int n = buf.length();
        while (i < n) {
            char c = buf.charAt(i);
            if (!CitationValidator.isOpenBracket(c)) {
                out.append(c);
                i++;
                continue;
            }

            // 从起始符向后解析：允许中间夹空白（模型偶尔写 [ 1 ]），最多看 MAX_MARKER_SPAN 个字符
            int j = i + 1;
            StringBuilder digits = new StringBuilder(MAX_REF_DIGITS);
            boolean closed = false;
            boolean notCitation = false;
            while (j < n && j - i <= MAX_MARKER_SPAN) {
                char d = buf.charAt(j);
                if (d >= '0' && d <= '9') {
                    digits.append(d);
                    j++;
                    if (digits.length() > MAX_REF_DIGITS) {
                        notCitation = true;
                        break;
                    }
                } else if (d == ' ' || d == '\t') {
                    j++;
                } else if (CitationValidator.isCloseBracket(d)) {
                    closed = true;
                    j++;
                    break;
                } else {
                    notCitation = true;
                    break;
                }
            }

            if (closed && digits.length() >= 1 && digits.length() <= MAX_REF_DIGITS) {
                int refNo = Integer.parseInt(digits.toString());
                if (CitationValidator.isValidMarker(refNo, itemCount)) {
                    // 合法：归一为半角（前端与后续解析只认 [n]）
                    out.append('[').append(refNo).append(']');
                    references.computeIfAbsent(refNo, k -> KnowledgeReference.from(hits.get(k - 1)));
                } else {
                    // 越界：删除并记账；顺手吞掉紧跟的一个空格，避免留下「依据不足  。」
                    droppedCount++;
                    if (j < n && buf.charAt(j) == ' ') {
                        j++;
                    }
                }
                i = j;
                continue;
            }

            if (!closed && !notCitation && j >= n && digits.length() <= MAX_REF_DIGITS) {
                // 到增量末尾仍未闭合：可能被拆成了 [1 与 2] 两段，先扣住等下一段
                holdback = buf.substring(i);
                break;
            }

            // 确认不是引用（[2024] / [abc / [ ] / 超长）：原样放行，绝不吞正文
            out.append(c);
            i++;
        }
        return emit(out.toString());
    }

    /** 流结束：放行剩余尾巴。未闭合的标记原样输出，宁可露出半截括号也不丢正文。 */
    public String flush() {
        String tail = holdback;
        holdback = "";
        if (tail.isEmpty() || truncated) {
            return "";
        }
        return emit(tail);
    }

    /** 已下发的完整正文（= 用户看到的内容 = 落库内容） */
    public String answer() {
        return emitted.toString();
    }

    /** 是否因为超过 max-answer-chars 而截断 */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * 门控统计结果，供编排器组装 references / droppedCount。
     *
     * <p>cleanAnswer 就是 {@link #answer()}，不要再拿它过一遍 {@code validate()}。
     */
    public CitationValidator.CitationCheckResult toCheckResult() {
        CitationValidator.CitationCheckResult result = new CitationValidator.CitationCheckResult();
        result.setCleanedAnswer(emitted.toString());
        result.setReferences(new ArrayList<>(references.values()));
        result.setDroppedCount(droppedCount);
        result.setAllDropped(references.isEmpty() && droppedCount > 0);
        return result;
    }

    /** 累加进已下发正文，并执行字符上限 */
    private String emit(String text) {
        if (text.isEmpty()) {
            return "";
        }
        int room = maxChars - emitted.length();
        if (room <= 0) {
            // 超上限后停止下发（不再抛异常打断上游：截断不是下游故障，
            // 走异常会被治理层记成失败并计入熔断）
            truncated = true;
            return "";
        }
        String value = text.length() <= room ? text : text.substring(0, room);
        if (value.length() < text.length()) {
            truncated = true;
        }
        emitted.append(value);
        return value;
    }
}
