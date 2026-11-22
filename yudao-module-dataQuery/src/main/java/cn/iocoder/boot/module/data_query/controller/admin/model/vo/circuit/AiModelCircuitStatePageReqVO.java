package cn.iocoder.boot.module.data_query.controller.admin.model.vo.circuit;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 模型熔断状态分页 Request VO
 */
@Schema(description = "管理后台 - 模型熔断状态分页 Request VO")
@Data
public class AiModelCircuitStatePageReqVO extends PageParam {

    @Schema(description = "熔断维度键，模糊匹配", example = "1:2")
    private String circuitKey;

    @Schema(description = "熔断状态：CLOSED / OPEN / HALF_OPEN", example = "OPEN")
    private String state;

    @Schema(description = "模型配置编号", example = "2")
    private Long modelConfigId;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
