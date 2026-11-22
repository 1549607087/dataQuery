package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

/**
 * 重跑类操作的「是否落库 / 是否切版本」判定（纯函数，无 Spring 依赖，便于单测）。
 *
 * <p>背景：对话树按「同一个问题的多个版本」组织，只有一个版本贴在墙上（is_active=1，
 * 且会话 current_leaf_id 指向它）。原实现不管本轮成功还是失败，都在 finish 阶段无条件切版本，
 * 于是「重新生成失败」会把用户原本正确的回答从视图里顶掉——数据还在，但被遮住了。
 *
 * <p>这里把「该不该贴到墙上」单独判定。普通提问（ASK）恒写：新的一轮即便失败也要留痕；
 * 重跑（REGENERATE / EDIT）失败则完全不写：旧版本原封不动。中断时只有在用户确实看到了内容
 * （pushedChars 达阈值）才写，避免产生一个「已停止生成」的空版本。
 */
public final class PromotionPolicy {

    public static final String OP_ASK = KnowledgeMessageTreeWriter.OP_ASK;
    public static final String OP_REGENERATE = KnowledgeMessageTreeWriter.OP_REGENERATE;
    public static final String OP_EDIT = KnowledgeMessageTreeWriter.OP_EDIT;

    private static final String STATE_DONE = "DONE";
    private static final String STATE_STOPPED = "STOPPED";

    private PromotionPolicy() {
    }

    /**
     * @param ctx          本轮问答上下文（终态已设置）
     * @param minKeepChars 中断时保留半句的最小下发字符数
     * @return true 才允许 TreeWriter 写库并切选中版本
     */
    public static boolean shouldPersist(KnowledgeChatContext ctx, int minKeepChars) {
        if (ctx == null) {
            return false;
        }
        String op = ctx.getMessageOp();
        boolean rerun = OP_REGENERATE.equals(op) || OP_EDIT.equals(op);
        if (!rerun) {
            // 普通提问：DONE / FAILED / REJECT 都要留痕，前两期行为不变
            return true;
        }
        if (STATE_DONE.equals(ctx.getTerminalState())) {
            return true;
        }
        return STATE_STOPPED.equals(ctx.getTerminalState())
                && ctx.getPushedChars() >= minKeepChars;
    }
}
