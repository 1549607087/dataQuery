package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 表关系 / Join 路径分页 Request VO
 */
@Schema(description = "管理后台 - 表关系 / Join 路径分页 Request VO")
@Data
public class AiDatasourceTableRelationPageReqVO extends PageParam {

    @Schema(description = "关联 ai_datasource.id；Join 路径不跨数据源")
    private Long datasourceId;

    @Schema(description = "左表，关联 ai_datasource_table.id")
    private Long leftTableId;

    @Schema(description = "右表，关联 ai_datasource_table.id")
    private Long rightTableId;

    @Schema(description = "是否已用真实 SQL 验证可执行：0 否、1 是")
    private Integer verified;

    @Schema(description = "是否启用：0 否、1 是")
    private Integer enabled;

    @Schema(description = "状态：0 正常、1 停用")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
