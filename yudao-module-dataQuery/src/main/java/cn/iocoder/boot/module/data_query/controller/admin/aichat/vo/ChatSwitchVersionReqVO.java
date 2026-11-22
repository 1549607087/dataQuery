package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 切换版本请求（P7）
 *
 * 复用「回答版本」接口：GET /message/versions 查询某轮全部版本，
 * PUT /message/versions 用本请求把选中版本切到 targetMessageId。
 * target 可为回答版本（USER 下的 ASSISTANT）或问题版本（USER），
 * 服务端按目标消息自身的 parent/role/seq 定位兄弟组，逻辑统一。
 */
@Schema(description = "管理后台 - 切换版本请求")
@Data
public class ChatSwitchVersionReqVO {

    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会话编号不能为空")
    private Long sessionId;

    @Schema(description = "目标版本消息编号（来自 versions 列表）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标版本消息编号不能为空")
    private Long targetMessageId;
}
