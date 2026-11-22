package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 反馈 Response VO
 *
 * 不回显 tenantId / deleted / creator / updater 等内部字段；
 * 审计表内容可能含 SQL 文本，除上述内部字段外完整回显。
 */
@Schema(description = "管理后台 - 反馈 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiQueryFeedbackRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_query_audit.id，本次反馈对应的问答")
    @ExcelProperty("关联 ai_query_au")
    private Long auditId;

    @Schema(description = "关联 ai_query_session.id")
    @ExcelProperty("关联 ai_query_se")
    private Long sessionId;

    @Schema(description = "反馈用户 ID")
    @ExcelProperty("反馈用户 ID")
    private Long userId;

    @Schema(description = "评分：1~5，5 为最满意")
    @ExcelProperty("评分")
    private Integer rating;

    @Schema(description = "反馈类型：如 结果不准 / SQL 错误 / 口径疑问 / 其它")
    @ExcelProperty("反馈类型")
    private String feedbackType;

    @Schema(description = "用户文字反馈")
    @ExcelProperty("用户文字反馈")
    private String comment;

    @Schema(description = "用户纠正后的 SQL，仅作参考；反馈不自动改写线上提示词版本，人工审核后才可进 few-shot")
    @ExcelProperty("用户纠正后的 SQL")
    private String correctedSql;

    @Schema(description = "审核状态：0 待审、1 已采纳、2 已驳回")
    @ExcelProperty("审核状态")
    private Integer reviewed;

    @Schema(description = "审核人用户 ID")
    @ExcelProperty("审核人用户 ID")
    private Long reviewerId;

    @Schema(description = "审核时间")
    @ExcelProperty("审核时间")
    private LocalDateTime reviewTime;

    @Schema(description = "审核备注")
    @ExcelProperty("审核备注")
    private String reviewRemark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
