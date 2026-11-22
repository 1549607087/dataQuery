package cn.iocoder.boot.module.data_query.controller.admin.model.vo.experiment;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 灰度实验分页 Request VO
 */
@Schema(description = "管理后台 - 灰度实验分页 Request VO")
@Data
public class AiModelGrayExperimentPageReqVO extends PageParam {

    @Schema(description = "实验名称，模糊匹配")
    private String name;

    @Schema(description = "灰度变量：MODEL / PROMPT")
    private String grayObject;

    @Schema(description = "生效场景编码", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "状态：0 草稿、1 进行中、2 已结束、3 已全量、4 已回滚")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
