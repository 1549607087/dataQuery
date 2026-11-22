package cn.iocoder.boot.module.data_query.framework.knowledge.chunk;

import cn.iocoder.boot.module.data_query.framework.knowledge.parser.DocumentTextRewriter;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.ParsedBlock;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构优先 + 递归字符降级的切片器。
 *
 * 策略（见 docs/knowledge-rag-flow.md）：
 *  1. 结构优先：解析器产出的结构块先分流成「逻辑段」——
 *     - < MIN_MERGE_TOKENS 的碎片向后合并；
 *     - >= MIN_MERGE_TOKENS 的块单独成段（尊重结构边界，不与相邻块硬凑）；
 *     - 表格叙述块（TABLE_NARRATION）与前后讲解正文并入同一段；
 *     - 短 TITLE 标题（如「二、制定依据」）与紧随的大正文并入同一段，避免几字标题单独落单；
 *     - 单块超过 chunkSize 时独立成段，交给下一阶段递归拆。
 *  2. 递归字符降级：段内若超长，按分隔符优先级逐级切分——
 *     段落(\n\n) > 换行(\n) > 句末(。！？；!?;) > 逗号(，,、) > 空格/制表符 > 字符硬切；
 *     每一级切出的子片段若仍超长，用下一级分隔符继续切，直到每片 <= chunkSize。
 *     切点吸附到语义边界，只把分隔符留在片段末尾，不丢字。
 *  3. 重叠：段内按滑窗打包，下一片起点回退 overlap 并吸附到片段边界；
 *     段与段之间同样回退 overlap，保证任意相邻 chunk 都有上下文衔接。
 *  4. token 用字符近似：CJK 一字≈1 token，其余（ASCII 等）约 4 字符≈1 token，零依赖。
 *  5. 所有 charStart/charEnd 基于解析后全文，前端可据此高亮原文。
 *
 * <p>注：切点算法已固化为「递归字符降级」，与旧的「定步长滑窗 + 单级边界吸附」不同，
 * content_hash 会整体变化，需对全库文档 rebuild 一次。
 */
@Component
public class ChunkSplitter {

    /** 低于此 token 数的块视为碎片，向后合并 */
    private static final int MIN_MERGE_TOKENS = 100;

    /** 单片至少保留的 token 预算占比，防止切出过小碎片 */
    private static final double MIN_SNAP_RATIO = 0.6;

    /** 递归降级分隔符：段落 > 换行 > 句末 > 逗号 > 空白；用尽后按字符硬切 */
    private static final String[] SEPARATORS = {"\n\n", "\n", "。！？；!?;", "，,、", " \t"};

    /** 自然句末标点，用于段间 overlap 回退时吸附边界 */
    private static final String SENTENCE_END = "。！？；!?;\n";

    /** 标题块类型标记（由 DocxParser 写入；MD 标题由官方 Reader 拼回正文，不再单独成块） */
    private static final String KIND_TITLE = "TITLE";

    // ==================== token 估算（公共 API，保持不变） ====================

    /** CJK 统一表意文字判定（含扩展区），命中记 1 token；其余按 0.25 token/字符 */
    private static boolean isCjk(char c) {
        int cp = c;
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x20000 && cp <= 0x2FFFF);
    }

    private static double charCost(char c) {
        return isCjk(c) ? 1.0 : 0.25;
    }

    /** 近似 token 数（四舍五入，至少 1） */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < text.length(); i++) {
            sum += charCost(text.charAt(i));
        }
        return Math.max(1, (int) Math.round(sum));
    }

    // ==================== 主流程 ====================

    /**
     * 切块主入口。
     *
     * @param doc        解析结果
     * @param chunkSize  单块 token 上限（配置 chunk-size，默认 500）
     * @param overlap    相邻块重叠 token 数（配置 chunk-overlap-ratio，默认 20%）
     * @return 带序号与溯源信息的切片草稿
     */
    public List<ChunkDraft> split(ParsedDocument doc, int chunkSize, int overlap) {
        List<ChunkDraft> result = new ArrayList<>();
        if (doc == null || doc.getBlocks() == null || doc.getBlocks().isEmpty()) {
            return result;
        }
        if (chunkSize <= 0) {
            chunkSize = 500;
        }
        if (overlap < 0 || overlap >= chunkSize) {
            overlap = Math.max(0, chunkSize / 10);
        }

        // 阶段 1：结构块分流成逻辑段（表格叙述块与前后正文同框）
        List<Segment> segments = buildSegments(doc.getBlocks(), chunkSize);

        // 阶段 2+3：段内递归字符拆分 + 带重叠打包；段间也回退 overlap
        int prevCut = -1;      // 上一段最后一片的终点（全文绝对下标）
        int prevSegStart = -1; // 上一段起点，限制回退不过头
        for (Segment seg : segments) {
            String text = doc.slice(seg.start, seg.end);
            if (text.isEmpty()) {
                continue;
            }
            List<Piece> pieces = recursiveSplit(text, seg.start, seg.meta, chunkSize);
            double[] prefix = prefixTokenCost(text);
            int[] pieceEnds = new int[pieces.size()];
            for (int i = 0; i < pieces.size(); i++) {
                pieceEnds[i] = pieces.get(i).end - seg.start;
            }

            int cursor = 0;
            int lastCut = 0;
            while (cursor < text.length()) {
                // token 预算允许的最远字符位置（保证本片 <= chunkSize）
                int hardEnd = upperBound(prefix, prefix[cursor] + chunkSize);
                if (hardEnd <= cursor) {
                    hardEnd = Math.min(cursor + 1, text.length());
                }
                int minPos = lowerBound(prefix,
                        prefix[cursor] + Math.max(1, (int) (chunkSize * MIN_SNAP_RATIO)));
                // 切点吸附到最近的自然片段边界（段/句/逗号），找不到则维持硬切点
                int cut = snapEnd(pieceEnds, minPos, hardEnd, cursor);

                int emitStart = seg.start + cursor;
                // 段的首片：从上一片回退 overlap（吸附句末边界），让段与段之间也有重叠
                if (cursor == 0 && prevCut >= 0 && overlap > 0) {
                    int back = Math.max(prevCut - overlap, prevSegStart + 1);
                    back = snapBackToBoundary(doc.getFullText(), back, seg.start);
                    if (back < emitStart) {
                        emitStart = back;
                    }
                }
                result.add(draft(doc, emitStart, seg.start + cut, metaAt(pieces, seg.start + cursor, seg.start + cut)));
                lastCut = cut;
                if (cut >= text.length()) {
                    break;
                }

                // 下一片起点回退 overlap token，再向前吸附到片段边界
                int backPos = upperBound(prefix, Math.max(prefix[cursor], prefix[cut] - overlap));
                if (backPos <= cursor) {
                    backPos = Math.min(cursor + 1, cut);
                }
                int next = snapStart(pieceEnds, backPos, cut, Math.max(1, overlap / 2));
                if (next <= cursor) {
                    next = Math.min(cursor + 1, cut);
                }
                if (next >= cut) {
                    next = Math.max(cursor + 1, cut - 1);
                }
                cursor = next;
            }
            prevCut = seg.start + lastCut;
            prevSegStart = seg.start;
        }

        // 统一重排序号，保证连续
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setChunkIndex(i);
        }
        return result;
    }

    // ==================== 阶段 1：结构块 -> 逻辑段 ====================

    /**
     * 结构块分流：小碎片向后合并；大块单独成段；表格叙述块与前后正文并入同段。
     * 单块超长也独立成段，交给递归字符拆分处理。
     */
    private List<Segment> buildSegments(List<ParsedBlock> blocks, int chunkSize) {
        List<Segment> segments = new ArrayList<>();
        List<ParsedBlock> pending = new ArrayList<>();
        int pendingTokens = 0;
        boolean narrationPending = false;

        for (int idx = 0; idx < blocks.size(); idx++) {
            ParsedBlock block = blocks.get(idx);
            if (block.effectiveLength() == 0) {
                continue;
            }
            int tokens = estimateTokens(block.getText());
            boolean narration = DocumentTextRewriter.KIND_TABLE_NARRATION.equals(block.getKind());
            boolean nextNarration = idx + 1 < blocks.size()
                    && DocumentTextRewriter.KIND_TABLE_NARRATION.equals(blocks.get(idx + 1).getKind());

            if (narration) {
                if (!pending.isEmpty() && pendingTokens + tokens > chunkSize) {
                    flushSegment(pending, segments);
                    pending.clear();
                    pendingTokens = 0;
                    narrationPending = false;
                }
                pending.add(block);
                pendingTokens += tokens;
                narrationPending = true;
                if (pendingTokens >= chunkSize * 0.9) {
                    flushSegment(pending, segments);
                    pending.clear();
                    pendingTokens = 0;
                    narrationPending = false;
                }
                continue;
            }

            if (narrationPending) {
                if (pendingTokens + tokens <= chunkSize) {
                    pending.add(block);
                    pendingTokens += tokens;
                    if (pendingTokens >= chunkSize * 0.8) {
                        flushSegment(pending, segments);
                        pending.clear();
                        pendingTokens = 0;
                        narrationPending = false;
                    }
                    continue;
                }
                flushSegment(pending, segments);
                pending.clear();
                pendingTokens = 0;
                narrationPending = false;
            }

            if (tokens < MIN_MERGE_TOKENS) {
                pending.add(block);
                pendingTokens += tokens;
                if (pendingTokens >= MIN_MERGE_TOKENS && !nextNarration) {
                    flushSegment(pending, segments);
                    pending.clear();
                    pendingTokens = 0;
                }
            } else {
                // 标题绑定：若 pending 尾部是 TITLE 块（如「二、制定依据」），当前又是大正文块，
                // 则不能先把标题单独 flush 成一片——标题 token 极小，落单后只剩几个字。
                // 改为把大正文并入 pending 一起成段，标题自然留在段首，与正文同片。
                // 若合计超过 chunkSize，交给阶段 2 递归拆分，标题仍与正文首片同框。
                boolean tailIsTitle = !pending.isEmpty()
                        && KIND_TITLE.equals(pending.get(pending.size() - 1).getKind());
                if (tailIsTitle) {
                    pending.add(block);
                    pendingTokens += tokens;
                    flushSegment(pending, segments);
                    pending.clear();
                    pendingTokens = 0;
                    continue;
                }
                if (nextNarration && pendingTokens + tokens <= chunkSize) {
                    pending.add(block);
                    pendingTokens += tokens;
                    continue;
                }
                flushSegment(pending, segments);
                pending.clear();
                pendingTokens = 0;
                segments.add(singleSegment(block));
            }
        }
        flushSegment(pending, segments);
        return segments;
    }

    private void flushSegment(List<ParsedBlock> pending, List<Segment> segments) {
        if (pending.isEmpty()) {
            return;
        }
        int start = pending.get(0).getCharStart();
        int end = pending.get(pending.size() - 1).getCharEnd();
        String heading = firstNonEmptyHeading(pending);
        Integer pageNo = pending.get(0).getPageNo();
        String kind = pending.get(0).getKind();
        segments.add(new Segment(start, end, new Meta(heading, pageNo, kind)));
    }

    private Segment singleSegment(ParsedBlock block) {
        String heading = block.getHeadingPath() == null ? "" : block.getHeadingPath();
        return new Segment(block.getCharStart(), block.getCharEnd(),
                new Meta(heading, block.getPageNo(), block.getKind()));
    }

    private String firstNonEmptyHeading(List<ParsedBlock> pending) {
        for (ParsedBlock b : pending) {
            if (b.getHeadingPath() != null && !b.getHeadingPath().isEmpty()) {
                return b.getHeadingPath();
            }
        }
        return "";
    }

    // ==================== 阶段 2：段内递归字符降级拆分 ====================

    /**
     * 对段文本递归拆分：按段落 > 换行 > 句末 > 逗号 > 空白逐级找分隔符；
     * 切出的子片段若仍超长则用下一级继续切；无分隔符可用时按 token 硬切。
     * 返回的片段首尾相接、完整覆盖输入区间，分隔符保留在前一片末尾。
     */
    private List<Piece> recursiveSplit(String text, int base, Meta meta, int chunkSize) {
        List<Piece> out = new ArrayList<>();
        splitRecursive(text, base, 0, meta, chunkSize, out);
        return out;
    }

    private void splitRecursive(String text, int base, int level, Meta meta, int chunkSize, List<Piece> out) {
        if (estimateTokens(text) <= chunkSize) {
            out.add(new Piece(base, base + text.length(), meta));
            return;
        }
        // 逐级降级：从当前级开始，找第一个能把文本切成多段的分隔符。
        // 关键：本切不出时必须继续试下一级，不能因为 \n\n 不存在就直接硬切。
        for (int lv = level; lv < SEPARATORS.length; lv++) {
            List<int[]> parts = splitKeepSeparator(text, SEPARATORS[lv]);
            if (parts.size() > 1) {
                for (int[] part : parts) {
                    splitRecursive(text.substring(part[0], part[1]), base + part[0], lv + 1,
                            meta, chunkSize, out);
                }
                return;
            }
        }
        // 分隔符用尽（字符级）：按 token 预算硬切，保证每片 <= chunkSize
        hardSplit(text, base, meta, chunkSize, out);
    }

    /** 按分隔符切成 [start,end) 片段，分隔符归属前一片末尾（keep separator），保证不丢字 */
    private List<int[]> splitKeepSeparator(String text, String separator) {
        List<int[]> parts = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int idx = text.indexOf(separator, from);
            if (idx < 0) {
                break;
            }
            int end = idx + separator.length();
            if (end > from) {
                parts.add(new int[]{from, end});
            }
            from = end;
        }
        if (from < text.length()) {
            parts.add(new int[]{from, text.length()});
        }
        return parts;
    }

    private void hardSplit(String text, int base, Meta meta, int chunkSize, List<Piece> out) {
        double[] prefix = prefixTokenCost(text);
        int cursor = 0;
        while (cursor < text.length()) {
            int end = upperBound(prefix, prefix[cursor] + chunkSize);
            if (end <= cursor) {
                end = Math.min(cursor + 1, text.length());
            }
            out.add(new Piece(base + cursor, base + end, meta));
            cursor = end;
        }
    }

    // ==================== 阶段 3：重叠与边界吸附 ====================

    /** 在 [minPos, hardEnd] 内取最大的片段边界作为切点；没有可用边界则维持硬切点 */
    private int snapEnd(int[] pieceEnds, int minPos, int hardEnd, int cursor) {
        int best = hardEnd;
        for (int b : pieceEnds) {
            if (b > cursor && b >= minPos && b <= hardEnd) {
                best = b;
            }
        }
        return best;
    }

    /** 在 [from, to) 内取第一个片段边界作为下一片起点；太贴近 to 则用硬回退起点保住重叠量 */
    private int snapStart(int[] pieceEnds, int from, int to, int minOverlap) {
        for (int b : pieceEnds) {
            if (b >= from && b < to) {
                return to - b < minOverlap ? from : b;
            }
        }
        return from;
    }

    /** 在 [from, to) 内从后往前找句末/换行边界，返回边界后一位；找不到则返回 from */
    private int snapBackToBoundary(String fullText, int from, int to) {
        if (fullText == null || from >= to) {
            return from;
        }
        int hi = Math.min(to, fullText.length());
        for (int i = hi - 1; i >= from; i--) {
            if (SENTENCE_END.indexOf(fullText.charAt(i)) >= 0) {
                int candidate = i + 1;
                // 跳过紧邻 to 的分隔符（块间 \n 等）：它属于下一个结构块，不能当作上一片的句末，
                // 否则回退位置会落到 to 本身，等于没回退、重叠为 0。
                if (candidate < to) {
                    return candidate;
                }
            }
        }
        return from;
    }

    /** 取覆盖 [from,to) 的第一个片段的溯源元信息 */
    private Meta metaAt(List<Piece> pieces, int from, int to) {
        for (Piece p : pieces) {
            if (p.end > from && p.start < to) {
                return p.meta;
            }
        }
        return pieces.get(pieces.size() - 1).meta;
    }

    // ==================== 工具 ====================

    private ChunkDraft draft(ParsedDocument doc, int start, int end, Meta meta) {
        String content = doc.slice(start, end);
        return ChunkDraft.builder()
                .chunkIndex(0) // 末尾统一重排
                .content(content)
                .tokenCount(estimateTokens(content))
                .charStart(start)
                .charEnd(end)
                .pageNo(meta.pageNo)
                .headingPath(meta.heading == null ? "" : meta.heading)
                .kind(meta.kind == null ? "PARAGRAPH" : meta.kind)
                .build();
    }

    /** prefix[i] = text[0..i) 的累计 token 成本 */
    private double[] prefixTokenCost(String text) {
        double[] prefix = new double[text.length() + 1];
        for (int i = 0; i < text.length(); i++) {
            prefix[i + 1] = prefix[i] + charCost(text.charAt(i));
        }
        return prefix;
    }

    /** 返回第一个 prefix[i] >= target 的下标（prefix 单调不减） */
    private int lowerBound(double[] prefix, double target) {
        int lo = 0, hi = prefix.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (prefix[mid] < target - 0.0001) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** 返回最后一个 prefix[i] <= target 的下标（用于按 token 预算定位字符位置） */
    private int upperBound(double[] prefix, double target) {
        int lo = 0, hi = prefix.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (prefix[mid] <= target + 0.0001) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    // ==================== 内部数据结构 ====================

    /** 结构段：一个逻辑段在全文中的区间 + 溯源元信息 */
    private static class Segment {
        final int start;
        final int end;
        final Meta meta;

        Segment(int start, int end, Meta meta) {
            this.start = start;
            this.end = end;
            this.meta = meta;
        }
    }

    /** 递归拆分后的达标片段 */
    private static class Piece {
        final int start;
        final int end;
        final Meta meta;

        Piece(int start, int end, Meta meta) {
            this.start = start;
            this.end = end;
            this.meta = meta;
        }
    }

    /** 溯源元信息：标题路径 / 页码 / 块类型 */
    private static class Meta {
        final String heading;
        final Integer pageNo;
        final String kind;

        Meta(String heading, Integer pageNo, String kind) {
            this.heading = heading;
            this.pageNo = pageNo;
            this.kind = kind;
        }
    }
}
