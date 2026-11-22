package cn.iocoder.boot.module.data_query.framework.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SSE 增量回答事件（事件名 {@code delta}）。
 *
 * <p>与 {@link ChatStageEvent} 的分工：stage 描述「链路走到哪一步」，delta 只承载「新增的正文」。
 * 两者共用一条连接，前端一个 listener 按事件名分流即可。
 *
 * <p>向后兼容：{@code done} 事件仍然携带完整 answer，老前端忽略 delta 也能正常工作，
 * 因此不需要灰度开关。
 */
@Data
@Schema(description = "智能问数 / 知识库问答 SSE 增量事件")
public class ChatDeltaEvent {

    @Schema(description = "本次新增的正文片段（不保证是完整句子）")
    private String content;

    /** 递增序号，从 1 开始；前端可用它丢弃乱序/重复帧 */
    @Schema(description = "增量序号")
    private Integer seq;

    @Schema(description = "链路编号，与 stage 事件一致")
    private String traceId;

    @Schema(description = "事件生成时间戳")
    private Long timestamp = System.currentTimeMillis();
}
