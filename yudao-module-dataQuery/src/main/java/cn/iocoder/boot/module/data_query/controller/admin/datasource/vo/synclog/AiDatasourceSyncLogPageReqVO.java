package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.synclog;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 数据源同步日志分页 Request VO
 */
@Schema(description = "管理后台 - 数据源同步日志分页 Request VO")
@Data
public class AiDatasourceSyncLogPageReqVO extends PageParam {

    @Schema(description = "关联 ai_datasource.id")
    private Long datasourceId;

    @Schema(description = "任务批次号，同一批次的多条明细共用，模糊匹配")
    private String taskNo;

    @Schema(description = "任务类型：1 连通性检测、2 全量元数据同步、3 增量元数据同步、4 结构变更检测、5 数据预览采样、6 枚举值采集")
    private Integer syncType;

    @Schema(description = "触发方式：1 手动、2 定时、3 创建后自动、4 结构变更告警")
    private Integer triggerType;

    @Schema(description = "状态：0 进行中、1 成功、2 部分成功、3 失败")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
