package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 问数结果反馈请求（P6 反馈闭环）。
 *
 * 与 P2-B 的通用 /ai-query-feedback/create 不同：这里刻意【不接受】
 * userId / sessionId / tenantId / reviewed / reviewerId / reviewTime / reviewRemark，
 * 全部由服务端从登录态与 auditId 对应的审计记录固化，防止前端伪造他人评分或自审自批。
 *
 * correctedSql 只作为人工审核的参考素材落库，永远不自动执行、不自动改写线上提示词版本。
 */
@Schema(description = "管理后台 - 问数结果反馈请求")
@Data
public class AiChatFeedbackReqVO {

    @Schema(description = "关联 ai_query_audit.id，即本次问数的查询审计编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "查询审计编号不能为空")
    private Long auditId;

    @Schema(description = "评分：1~5，5 为最满意", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为 1")
    @Max(value = 5, message = "评分最大为 5")
    private Integer rating;

    @Schema(description = "反馈类型：结果不准 / SQL 错误 / 口径疑问 / 其它", example = "结果不准")
    @Size(max = 64, message = "反馈类型长度不能超过 64")
    private String feedbackType;

    @Schema(description = "文字反馈", example = "金额口径应为含税金额")
    @Size(max = 500, message = "文字反馈长度不能超过 500")
    private String comment;

    @Schema(description = "用户纠正后的 SQL（仅作参考，需人工审核）")
    @Size(max = 4000, message = "纠正 SQL 长度不能超过 4000")
    private String correctedSql;
}
