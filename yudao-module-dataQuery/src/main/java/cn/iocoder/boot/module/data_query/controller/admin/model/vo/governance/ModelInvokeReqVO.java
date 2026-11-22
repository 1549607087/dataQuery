package cn.iocoder.boot.module.data_query.controller.admin.model.vo.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 管理后台 - 模型治理链路联调 Request VO
 *
 * 仅用于验证「路由 → 限流 → 熔断 → 超时 → 重试 → 降级 → 日志」是否打通。
 * 未接入真实 ModelClient 时返回 echo = true 的占位结果，不能作为业务数据使用。
 */
@Schema(description = "管理后台 - 模型治理链路联调 Request VO")
@Data
public class ModelInvokeReqVO {

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL_GEN")
    @NotEmpty(message = "场景编码不能为空")
    private String sceneCode;

    @Schema(description = "提示词正文", example = "查询本月销售额")
    private String prompt;

    @Schema(description = "全链路追踪 ID，留空自动生成")
    private String traceId;

    @Schema(description = "会话编号")
    private Long conversationId;

    @Schema(description = "用户编号（USER 分桶用）")
    private Long userId;

    @Schema(description = "租户编号（TENANT 分桶用）")
    private Long tenantId;

    @Schema(description = "会话标识（SESSION 分桶用）")
    private String sessionId;

}
