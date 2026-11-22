package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 反馈新增/修改 Request VO
 *
 * tenant_id / creator / updater / create_time / update_time / deleted 一律由服务端写入，
 * 禁止前端传入；主键 id 为空表示新增。
 */
@Schema(description = "管理后台 - 反馈新增/修改 Request VO")
@Data
public class AiQueryFeedbackSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "关联 ai_query_audit.id，本次反馈对应的问答", example = "")
    private Long auditId;

    @Schema(description = "关联 ai_query_session.id", example = "")
    private Long sessionId;

    @Schema(description = "反馈用户 ID", example = "")
    private Long userId;

    @Schema(description = "评分：1~5，5 为最满意", example = "")
    @NotNull(message = "Rating不能为空")
    private Integer rating;

    @Schema(description = "反馈类型：如 结果不准 / SQL 错误 / 口径疑问 / 其它", example = "")
    private String feedbackType;

    @Schema(description = "用户文字反馈", example = "")
    private String comment;

    @Schema(description = "用户纠正后的 SQL，仅作参考；反馈不自动改写线上提示词版本，人工审核后才可进 few-shot", example = "")
    private String correctedSql;

    @Schema(description = "审核状态：0 待审、1 已采纳、2 已驳回", example = "")
    private Integer reviewed;

    @Schema(description = "审核人用户 ID", example = "")
    private Long reviewerId;

    @Schema(description = "审核时间", example = "")
    private LocalDateTime reviewTime;

    @Schema(description = "审核备注", example = "")
    private String reviewRemark;

}
