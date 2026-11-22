package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 提示词版本分页 Request VO
 */
@Schema(description = "管理后台 - 提示词版本分页 Request VO")
@Data
public class AiPromptVersionPageReqVO extends PageParam {

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long promptId;

    @Schema(description = "版本号", example = "1")
    private Integer version;

    @Schema(description = "状态：0 草稿、1 已发布、2 已回滚、3 归档", example = "1")
    private Integer status;

    @Schema(description = "发布人，模糊匹配")
    private String publisher;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
