package cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 模型供应商分页 Request VO
 */
@Schema(description = "管理后台 - 模型供应商分页 Request VO")
@Data
public class AiModelProviderPageReqVO extends PageParam {

    @Schema(description = "供应商名称，模糊匹配", example = "通义千问")
    private String name;

    @Schema(description = "供应商编码，精确匹配", example = "qwen")
    private String code;

    @Schema(description = "协议", example = "OPENAI")
    private String protocol;

    @Schema(description = "状态：0 启用、1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
