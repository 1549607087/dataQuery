package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import cn.iocoder.boot.module.data_query.framework.chat.ChartSpec;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 - 对话消息 Response VO（P7）
 *
 * 不回显 tenantId / deleted / creator / updater 等内部字段。
 * result 由 result_json 反序列化而来；只含本租户只读执行结果。
 */
@Schema(description = "管理后台 - 对话消息 Response VO")
@Data
public class ChatMessageRespVO {

    @Schema(description = "消息编号")
    private Long id;

    @Schema(description = "角色：USER 用户提问 / ASSISTANT 助手回答")
    private String role;

    @Schema(description = "父消息编号（ASSISTANT 指向 USER）")
    private Long parentId;

    @Schema(description = "版本（同一问题重新生成 / 修改后递增）")
    private Integer version;

    @Schema(description = "展示序号")
    private Integer seq;

    @Schema(description = "是否当前主链：true 是 / false 历史版本")
    private Boolean active;

    @Schema(description = "用户问题 / 该轮问题文本")
    private String question;

    @Schema(description = "助手回答文本 / 摘要")
    private String answer;

    @Schema(description = "生成的 SQL / 最终执行 SQL")
    private String generatedSql;
    private String executedSql;

    @Schema(description = "查询结果快照（助手消息，可能是历史结果）")
    private QueryResult result;

    @Schema(description = "图表推荐 / 口径说明")
    private ChartSpec chartSpec;
    private String explain;

    @Schema(description = "终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED")
    private String status;

    @Schema(description = "错误码 / 错误原因")
    private String errorCode;
    private String errorMsg;

    @Schema(description = "赞踩：-1 踩 / 0 未评价 / 1 赞")
    private Integer rating;

    @Schema(description = "链路追踪 ID / 关联审计编号")
    private String traceId;
    private Long auditId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "回答历史版本（仅 userMessageId 查版本时返回）")
    private List<ChatMessageRespVO> alternatives;
}
