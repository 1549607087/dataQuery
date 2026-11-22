package cn.iocoder.boot.module.data_query.framework.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P6 SSE 阶段事件。
 *
 * 事件名统一为 {@code stage}，用 {@link #stage} 字段区分阶段，前端只需一个 listener：
 * intent → rewrite → recalled → generated → validated → executing → result → summary → done/error。
 *
 * 安全边界：除 done/error 事件外，payload 只放状态、计数、模型代号、SQL 文本与结构化建议，
 * 不放业务数据行、连接串、凭证。唯一例外是 done 事件的 payload.response，
 * 内容与 /ask 响应完全同口径（含结果表格），由同一个 RespVO 序列化，不新开数据出口。
 */
@Data
@Schema(description = "智能问数 SSE 阶段事件")
public class ChatStageEvent {

    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String SKIPPED = "SKIPPED";
    public static final String ERROR = "ERROR";
    /** 用户主动停止：终态用 stopped 阶段 + STOPPED 状态，前端据此显示「已停止」而非成功/失败 */
    public static final String STOPPED = "STOPPED";

    /** 阶段名：intent / rewrite / recalled / generated / validated / executing / result / summary / done / error / stopped */
    @Schema(description = "阶段名")
    private String stage;

    /** RUNNING / DONE / SKIPPED / ERROR */
    @Schema(description = "阶段状态")
    private String state;

    @Schema(description = "链路编号，与应用日志 / 模型调用日志 / 查询审计串联")
    private String traceId;

    @Schema(description = "面向用户的阶段说明")
    private String message;

    @Schema(description = "进度百分比 0~100")
    private Integer progress;

    @Schema(description = "阶段附加信息（不含业务数据行、连接串、凭证）")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Schema(description = "自请求开始的耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "事件生成时间戳")
    private Long timestamp = System.currentTimeMillis();

    public static ChatStageEvent of(String stage, String state, String message, int progress) {
        ChatStageEvent event = new ChatStageEvent();
        event.setStage(stage);
        event.setState(state);
        event.setMessage(message);
        event.setProgress(progress);
        return event;
    }

    public ChatStageEvent put(String key, Object value) {
        if (key != null && value != null) {
            this.payload.put(key, value);
        }
        return this;
    }
}
