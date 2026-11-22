package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理后台 - 赞踩请求（P7）：-1 踩 / 0 取消 / 1 赞，仅 ASSISTANT 消息可评分 */
@Schema(description = "管理后台 - 赞踩请求")
@Data
public class ChatRatingReqVO {

    @Schema(description = "助手消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "消息编号不能为空")
    private Long messageId;

    @Schema(description = "评分：-1 踩 / 0 取消 / 1 赞", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评分不能为空")
    @Min(value = -1, message = "评分最小为 -1")
    @Max(value = 1, message = "评分最大为 1")
    private Integer rating;
}
