package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeChunkHit;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用校验器（防引用幻觉）
 *
 * <p>回答里的 [n] 由服务端在 context 中按召回切片连续编号生成（1..K）。模型可能编造越界编号，
 * 本类只保留落在 {@code [1, hitCount]} 的编号，越界编号直接删除，并只把被引用的切片
 * 组装成 references。
 *
 * <p>为什么不做「降级重写」：重写需要再调一次模型，成本与不确定性更高；直接删编号确定性强、可测试，
 * 前端可据 citationDroppedCount 提示「部分引用不可用」。
 *
 * <p>支持半角 {@code [1]}、全角 {@code 【1】} / {@code ［1］}，统一输出半角 {@code [n]}。
 */
@Component
public class CitationValidator {

    /** 1~3 位数字，避免把 [2024] 之类年份误判为引用 */
    private static final Pattern CITATION = Pattern.compile("[\\[【［]\\s*(\\d{1,3})\\s*[\\]】］]");

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern SPACE_AROUND_NEWLINE = Pattern.compile(" *\\n *");

    /** 引用编号上限位数：1~3 位，避免把 [2024] 之类年份误判为引用 */
    private static final int MAX_REF_DIGITS = 3;

    /** 半角 [ 、全角 【 、全角方括号 ［ 都可能是引用起始符 */
    public static boolean isOpenBracket(char c) {
        return c == '[' || c == '【' || c == '［';
    }

    /** 与起始符对应的三种闭合符（不强制配对：模型常写【1] 这种混搭） */
    public static boolean isCloseBracket(char c) {
        return c == ']' || c == '】' || c == '］';
    }

    /**
     * 引用编号是否在本轮上下文范围内 —— <b>同步清洗与流式门控的唯一判定来源</b>。
     *
     * <p>两处各写一遍规则必然漂移（一处改了另一处忘改），表现就是「流式看到的和刷新后看到的不一样」。
     */
    public static boolean isValidMarker(int refNo, int itemCount) {
        return refNo >= 1 && refNo <= itemCount;
    }

    /**
     * 校验并裁剪回答中的引用编号。
     *
     * @param answer 模型原始回答
     * @param hits   本次进入上下文的切片（按 refNo 升序，索引 i 对应 [i+1]）
     */
    public CitationCheckResult validate(String answer, List<KnowledgeChunkHit> hits) {
        CitationCheckResult result = new CitationCheckResult();
        result.setCleanedAnswer(answer);
        result.setReferences(new ArrayList<>());
        result.setDroppedCount(0);

        if (answer == null || answer.isEmpty() || hits == null || hits.isEmpty()) {
            return result;
        }

        int max = hits.size();
        Map<Integer, KnowledgeReference> refs = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder(answer.length());
        Matcher matcher = CITATION.matcher(answer);
        int last = 0;
        int dropped = 0;
        boolean hasCitation = false;

        while (matcher.find()) {
            hasCitation = true;
            int n = Integer.parseInt(matcher.group(1));
            out.append(answer, last, matcher.start());
            if (isValidMarker(n, max)) {
                out.append('[').append(n).append(']');
                refs.computeIfAbsent(n, k -> buildReference(hits.get(k - 1)));
            } else {
                dropped++;          // 越界编号直接删除，不保留 [n]
            }
            last = matcher.end();
        }
        out.append(answer, last, answer.length());

        // 有引用就重建：把全角【n】/［n］归一为半角 [n]（前端与后续解析只认半角）。
        // 只有真的删过编号才做空格清理，避免无意义地改动模型原文。
        String cleaned = hasCitation ? out.toString() : answer;
        if (dropped > 0) {
            cleaned = tidy(cleaned);
        }
        result.setCleanedAnswer(cleaned);
        result.setReferences(new ArrayList<>(refs.values()));
        result.setDroppedCount(dropped);
        result.setAllDropped(!refs.isEmpty() ? false : dropped > 0);
        return result;
    }

    private static KnowledgeReference buildReference(KnowledgeChunkHit hit) {
        return KnowledgeReference.from(hit);
    }

    /** 删除编号后可能留下连续空格/行首空格，做一次轻量清理 */
    private static String tidy(String text) {
        return SPACE_AROUND_NEWLINE.matcher(MULTI_SPACE.matcher(text).replaceAll(" ")).replaceAll("\n").trim();
    }

    /** 校验结果 */
    @Data
    public static class CitationCheckResult {
        /** 裁剪后的回答 */
        private String cleanedAnswer;
        /** 被引用的切片（按首次出现顺序去重） */
        private List<KnowledgeReference> references = new ArrayList<>();
        /** 被删除的越界编号数量 */
        private Integer droppedCount = 0;
        /** 原本有引用但全部越界 */
        private Boolean allDropped = false;
    }
}
