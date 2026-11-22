package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.template;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 查询模板分页 Request VO
 */
@Schema(description = "管理后台 - 查询模板分页 Request VO")
@Data
public class AiQueryTemplatePageReqVO extends PageParam {

    @Schema(description = "模板名称，模糊匹配")
    private String name;

    @Schema(description = "模板编码，租户内唯一，供程序引用")
    private String code;

    @Schema(description = "模板分类，如 销售 / 财务 / 运营")
    private String category;

    @Schema(description = "关联 ai_datasource.id，模板适用的数据源，0 表示通用")
    private Long datasourceId;

    @Schema(description = "是否启用：0 否、1 是")
    private Integer enabled;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
