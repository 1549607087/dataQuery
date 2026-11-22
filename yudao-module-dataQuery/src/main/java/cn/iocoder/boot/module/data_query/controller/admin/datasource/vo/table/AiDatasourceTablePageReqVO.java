package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.table;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 数据源表元数据分页 Request VO
 */
@Schema(description = "管理后台 - 数据源表元数据分页 Request VO")
@Data
public class AiDatasourceTablePageReqVO extends PageParam {

    @Schema(description = "关联 ai_datasource.id")
    private Long datasourceId;

    @Schema(description = "物理表名，模糊匹配")
    private String tableName;

    @Schema(description = "业务中文名，喂给 LLM 的 Schema 描述，模糊匹配")
    private String nameZh;

    @Schema(description = "对象类型：1 表、2 视图、3 物化视图、4 外部表")
    private Integer tableType;

    @Schema(description = "管理端是否可见：0 否、1 是")
    private Integer visible;

    @Schema(description = "是否允许 LLM 生成 SQL 时引用：0 否、1 是；静态校验的表白名单")
    private Integer queryable;

    @Schema(description = "同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除（失效）")
    private Integer syncStatus;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
