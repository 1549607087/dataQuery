package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.enumvalue;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 字段枚举映射分页 Request VO
 */
@Schema(description = "管理后台 - 字段枚举映射分页 Request VO")
@Data
public class AiDatasourceColumnEnumPageReqVO extends PageParam {

    @Schema(description = "关联 ai_datasource.id")
    private Long datasourceId;

    @Schema(description = "关联 ai_datasource_table.id")
    private Long tableId;

    @Schema(description = "关联 ai_datasource_column.id")
    private Long columnId;

    @Schema(description = "数据库中存储的原值，模糊匹配")
    private String sourceValue;

    @Schema(description = "业务显示名，拼入提示词让 LLM 理解「1 = 已支付」，模糊匹配")
    private String displayName;

    @Schema(description = "关联 system_dict_type.dict_type，空表示纯本地映射")
    private String dictType;

    @Schema(description = "状态：0 启用、1 停用")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
