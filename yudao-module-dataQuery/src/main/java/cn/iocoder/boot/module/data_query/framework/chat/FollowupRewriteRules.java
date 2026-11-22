package cn.iocoder.boot.module.data_query.framework.chat;

import java.util.List;

/**
 * 追问改写的纯规则（A 方案）——不依赖 Spring / Jackson / DB，可独立测试。
 *
 * <p>判定「当前问题是否为承接上文的追问」以及「如何拼接上轮上下文」都是确定性文本规则，
 * 与历史消息的读取（{@link FollowupRewriteService}）解耦，便于单测与后续替换成模型改写。</p>
 */
public final class FollowupRewriteRules {

    /** 句首强承接词：出现即以追问处理（多为承接上文的指代/延续） */
    private static final List<String> STRONG_PREFIX = List.of(
            "那", "那么", "这个", "那个", "这些", "那些", "它们", "它的", "其", "其中",
            "继续", "接着", "再说", "再看", "还有", "同样", "此外", "另外", "上面", "刚才");

    /** 句中/句尾承接短语：语义上必然依赖上文。
     *  刻意不收「各」「分别」——它们是完整问法常用词（「各产品线退货率分别是多少」），
     *  放进这里会把独立问题误判为追问。 */
    private static final List<String> CONTEXT_PHRASES = List.of(
            "换成", "改为", "改一下", "按月份", "按地区", "按天", "按周", "按年",
            "按月", "分别按", "在此基础上", "在这个基础");

    /** 上轮问题拼接截断上限，控制 Embedding 与 prompt 体积 */
    private static final int MAX_PREV_QUESTION = 200;
    /** 短问法阈值：≤6 字基本都是承接上文的追问（10 字会误伤「统计每个地区的销售额」） */
    private static final int SHORT_QUESTION_LEN = 6;

    private FollowupRewriteRules() {
    }

    /**
     * 是否为需要改写的追问。规则（满足任一）：
     * <ol>
     *   <li>以强承接词开头（那 / 这个 / 继续 / 其中 …）；</li>
     *   <li>包含上下文短语（换成 / 按月份 / 分别 / 在此基础上 …）；</li>
     *   <li>以语气词结尾（呢 / 吗 / 吧 / ？）；</li>
     *   <li>问题很短（≤ 10 字）——短问法基本都是承接上文。</li>
     * </ol>
     * 改写是召回输入增强而非替换，误判至多是多带一点上下文，不会破坏原问题。
     */
    public static boolean isFollowup(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String s = question.trim();
        for (String prefix : STRONG_PREFIX) {
            if (s.startsWith(prefix)) {
                return true;
            }
        }
        for (String phrase : CONTEXT_PHRASES) {
            if (s.contains(phrase)) {
                return true;
            }
        }
        if (s.endsWith("呢") || s.endsWith("吗") || s.endsWith("吧")
                || s.endsWith("？") || s.endsWith("?")) {
            return true;
        }
        return s.length() <= SHORT_QUESTION_LEN;
    }

    /**
     * 拼接自包含问题：当前问题置前保留原意，括号内补上轮问题与命中表作为召回信号。
     * 例：{@code 那按月份呢（承接上一轮：统计各地区的销售额；涉及表：ods_sales_region）}
     */
    public static String buildRewritten(String current, String prevQuestion, List<String> tableNames) {
        if (current == null || current.isBlank() || prevQuestion == null || prevQuestion.isBlank()) {
            return current;
        }
        StringBuilder sb = new StringBuilder(current);
        sb.append("（承接上一轮：").append(clip(prevQuestion.trim(), MAX_PREV_QUESTION));
        if (tableNames != null && !tableNames.isEmpty()) {
            sb.append("；涉及表：").append(String.join("、", tableNames));
        }
        sb.append("）");
        return sb.toString();
    }

    private static String clip(String value, int max) {
        return value == null ? null : (value.length() <= max ? value : value.substring(0, max));
    }
}
