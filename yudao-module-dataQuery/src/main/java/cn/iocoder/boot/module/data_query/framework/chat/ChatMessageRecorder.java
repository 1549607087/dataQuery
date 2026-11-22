package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.AiChatAskRespVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * P7 对话消息落库门面：校验归属后，把落库交给带事务的 {@link ChatMessageTreeWriter}。
 *
 * <p>职责边界：
 * <ul>
 *   <li>这里只做「是否可落库」判断 + 吞异常降级（落库失败只记日志，绝不影响 ask 终态）；</li>
 *   <li>三操作的树落库编排、事务、version 计算、兄弟切换、叶子推进全部在 Writer 内，见该类注释。</li>
 * </ul>
 *
 * <p>安全：消息归属由编排器 S0 固化的 userId / sessionId 决定；result_json 只含本租户只读执行结果。
 */
@Slf4j
@Component
public class ChatMessageRecorder {

    public static final String OP_ASK = "ASK";
    public static final String OP_REGENERATE = "REGENERATE";
    public static final String OP_EDIT = "EDIT";

    @Resource
    private ChatMessageTreeWriter treeWriter;

    /**
     * 落一条问答消息。返回 ASSISTANT 消息 id（失败返回 null，仅记日志，不影响 ask 结果）。
     */
    public Long record(QueryContext ctx, AiChatAskRespVO resp) {
        try {
            if (ctx.getSessionId() == null || ctx.getUserId() == null) {
                // S0 前置失败：无会话上下文，审计已留痕，消息不落库
                return null;
            }
            return treeWriter.write(ctx, resp);
        } catch (Exception ex) {
            // 事务方法抛出的异常在此降级：整笔已回滚，ask 终态不受影响
            log.warn("[chat][message-record] 消息落库失败 traceId={}, op={}, reason={}",
                    ctx.getTraceId(), ctx.getMessageOp(), ex.getMessage());
            return null;
        }
    }
}
