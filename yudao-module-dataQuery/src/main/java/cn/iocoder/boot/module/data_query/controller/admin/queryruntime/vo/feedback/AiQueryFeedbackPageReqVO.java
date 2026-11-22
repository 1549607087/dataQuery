package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.feedback;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 反馈分页 Request VO
 */
@Schema(description = "管理后台 - 反馈分页 Request VO")
@Data
public class AiQueryFeedbackPageReqVO extends PageParam {

    @Schema(description = "关联 ai_query_audit.id，本次反馈对应的问答")
    private Long auditId;

    @Schema(description = "关联 ai_query_session.id")
    private Long sessionId;

    @Schema(description = "反馈用户 ID")
    private Long userId;

    @Schema(description = "评分：1~5，5 为最满意")
    private Integer rating;

    @Schema(description = "反馈类型：如 结果不准 / SQL 错误 / 口径疑问 / 其它")
    private String feedbackType;

    @Schema(description = "审核状态：0 待审、1 已采纳、2 已驳回")
    private Integer reviewed;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
