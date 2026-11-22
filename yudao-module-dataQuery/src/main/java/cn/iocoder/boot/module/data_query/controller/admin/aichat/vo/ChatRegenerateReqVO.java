package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 重新生成请求（P7）
 *
 * 只接受 messageId（ ASSISTANT 消息）：问题文本 / 数据源 / 会话全部由服务端从消息固化，
 * 前端不能借重新生成绕过归属校验或注入新的问题文本。
 */
@Schema(description = "管理后台 - 重新生成请求")
@Data
public class ChatRegenerateReqVO {

    @Schema(description = "要重新生成的助手消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "消息编号不能为空")
    private Long messageId;

    @Schema(description = "可选：数据源编号；为空时沿用原消息的数据源")
    private Long datasourceId;

    @Schema(description = "可选：指定模型配置编号；为空时沿用场景默认路由，"
            + "非空时必须在当前场景候选集内")
    private Long modelConfigId;
}
