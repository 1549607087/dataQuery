package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 数据源分页 Request VO
 */
@Schema(description = "管理后台 - 数据源分页 Request VO")
@Data
public class AiDatasourcePageReqVO extends PageParam {

    @Schema(description = "数据源显示名称，模糊匹配")
    private String name;

    @Schema(description = "数据源编码，租户内唯一，供主题与程序引用")
    private String code;

    @Schema(description = "数据库类型：1 MySQL、2 PostgreSQL、3 Oracle、4 SQLServer、5 达梦、6 人大金仓、7 openGauss、8 ClickHouse、9 Hive")
    private Integer dsType;

    @Schema(description = "SQL 方言：postgresql / mysql / oracle / sqlserver / dm / clickhouse；供 ai_prompt_variable.source_key=datasource.dialect 注入")
    private String dialect;

    @Schema(description = "状态：0 启用、1 停用、2 维护中")
    private Integer status;

    @Schema(description = "健康状态：0 未检测、1 健康、2 异常")
    private Integer healthStatus;

    @Schema(description = "同步状态：0 未同步、1 同步中、2 成功、3 失败")
    private Integer syncStatus;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
