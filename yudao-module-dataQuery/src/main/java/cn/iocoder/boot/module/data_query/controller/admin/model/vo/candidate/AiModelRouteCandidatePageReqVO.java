package cn.iocoder.boot.module.data_query.controller.admin.model.vo.candidate;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 模型路由候选分页 Request VO
 */
@Schema(description = "管理后台 - 模型路由候选分页 Request VO")
@Data
public class AiModelRouteCandidatePageReqVO extends PageParam {

    @Schema(description = "场景绑定编号", example = "1")
    private Long bindingId;

    @Schema(description = "候选模型编号", example = "2")
    private Long modelId;

    @Schema(description = "供应商编号", example = "1")
    private Long providerId;

    @Schema(description = "是否参与路由：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
