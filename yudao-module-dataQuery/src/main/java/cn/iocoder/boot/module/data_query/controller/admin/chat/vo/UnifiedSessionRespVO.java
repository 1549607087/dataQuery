package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 统一会话列表项 Response VO。
 *
 * <p>由 ai_query_session 与 ai_knowledge_session 两张表 UNION ALL 聚合而来。
 * sessionId 是雪花 ID，两套表不冲突，但必须配 mode 才能定位到正确的表。
 */
@Schema(description = "管理后台 - 统一会话列表项 Response VO")
@Data
public class UnifiedSessionRespVO {

    @Schema(description = "业务类型：QUERY / KNOWLEDGE")
    private String mode;

    @Schema(description = "会话编号")
    private Long sessionId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "最近一次问题")
    private String lastQuestion;

    @Schema(description = "最近一次提问时间（排序键，降序）")
    private LocalDateTime lastAskTime;

    @Schema(description = "消息条数")
    private Integer messageCount;

    @Schema(description = "状态：1 进行中 / 2 已结束")
    private Integer status;

    @Schema(description = "链路编号")
    private String traceId;

    @Schema(description = "归属资源编号（QUERY 为 datasourceId，KNOWLEDGE 为 baseId）")
    private Long resourceId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
