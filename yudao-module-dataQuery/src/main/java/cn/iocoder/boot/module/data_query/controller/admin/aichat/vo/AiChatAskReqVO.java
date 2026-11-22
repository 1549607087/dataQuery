package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 智能问数 ask 请求 VO（P5）。
 *
 * 刻意只接受 question / datasourceId / sessionId / sceneCode / modelConfigId：
 * 不接受 schema 文本、表名、SQL、tenantId —— Schema 白名单只能由服务端 S3 召回生成，
 * 租户只能来自登录态，防止前端绕过召回或伪造他人租户。
 *
 * modelConfigId 只是「偏好」：服务端会校验它是否在当前场景的候选集内，
 * 不在候选集（停用 / 已删除 / 跨租户 / 未绑定到该场景）一律拒绝，不能借此绕开路由治理。
 */
@Schema(description = "管理后台 - 智能问数 ask 请求")
@Data
public class AiChatAskReqVO {

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "查询本月销售额前 10 的商品")
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源编号不能为空")
    private Long datasourceId;

    @Schema(description = "会话编号；为空时新建会话")
    private Long sessionId;

    @Schema(description = "场景编码；默认 SQL_GEN")
    @Size(max = 64, message = "场景编码长度不能超过 64")
    private String sceneCode;

    @Schema(description = "指定模型配置编号；为空时按场景绑定的路由策略自动选择，"
            + "非空时必须在当前场景候选集内")
    private Long modelConfigId;

}
