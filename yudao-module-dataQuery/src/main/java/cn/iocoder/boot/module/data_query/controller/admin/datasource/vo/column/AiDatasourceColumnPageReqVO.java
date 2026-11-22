package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.column;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 数据源字段元数据分页 Request VO
 */
@Schema(description = "管理后台 - 数据源字段元数据分页 Request VO")
@Data
public class AiDatasourceColumnPageReqVO extends PageParam {

    @Schema(description = "关联 ai_datasource.id，冗余便于按数据源检索")
    private Long datasourceId;

    @Schema(description = "关联 ai_datasource_table.id")
    private Long tableId;

    @Schema(description = "物理表名，冗余便于拼接 SQL，模糊匹配")
    private String tableName;

    @Schema(description = "物理列名，模糊匹配")
    private String columnName;

    @Schema(description = "字段业务中文名，口径解释与列裁剪用，模糊匹配")
    private String nameZh;

    @Schema(description = "语义角色：1 维度、2 指标、3 时间、4 主键、5 外键、6 标识/无关、7 敏感")
    private Integer semanticRole;

    @Schema(description = "是否允许被 LLM 选中：0 否、1 是；静态校验的列白名单，越权列的最后一道防线")
    private Integer queryable;

    @Schema(description = "敏感类型：0 无、1 手机号、2 身份证、3 姓名、4 邮箱、5 银行卡、6 金额、9 自定义")
    private Integer sensitiveType;

    @Schema(description = "同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除")
    private Integer syncStatus;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
