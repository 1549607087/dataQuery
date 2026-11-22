package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 修改问题请求（P7）
 *
 * 只接受 messageId（USER 消息）+ 改写后的问题文本；会话 / 用户 / 租户由服务端固化。
 */
@Schema(description = "管理后台 - 修改问题请求")
@Data
public class ChatEditReqVO {

    @Schema(description = "要修改的用户消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "消息编号不能为空")
    private Long messageId;

    @Schema(description = "改写后的问题文本", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "可选：数据源编号；为空时沿用原消息的数据源")
    private Long datasourceId;

    @Schema(description = "可选：指定模型配置编号；为空时沿用场景默认路由，"
            + "非空时必须在当前场景候选集内")
    private Long modelConfigId;
}
