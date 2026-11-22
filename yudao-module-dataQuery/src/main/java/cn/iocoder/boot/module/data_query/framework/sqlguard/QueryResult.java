package cn.iocoder.boot.module.data_query.framework.sqlguard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * S7 只读执行结果（P4 阶段：表格 + 执行统计 + 审计编号）。
 *
 * P5/P6 会在此之上补 chartSpec / summary / explain / SSE 阶段事件；
 * 本阶段不含任何模型解读，也不缓存结果集（结果只归本次请求）。
 */
@Data
@Schema(description = "S7 只读查询执行结果")
public class QueryResult {

    @Schema(description = "查询审计编号")
    private Long auditId;

    @Schema(description = "链路追踪 ID")
    private String traceId;

    @Schema(description = "实际执行的 SQL（已过 S6 闸门）")
    private String executedSql;

    @Schema(description = "结果列名")
    private List<String> columnNames = new ArrayList<>();

    @Schema(description = "结果列 JDBC 类型名")
    private List<String> columnTypes = new ArrayList<>();

    @Schema(description = "行数据（按 columnNames 顺序，已按 mask_rule 脱敏）")
    private List<List<Object>> rows = new ArrayList<>();

    @Schema(description = "返回行数")
    private Integer rowCount;

    @Schema(description = "是否触达 max_rows 上限（可能还有更多行）")
    private Boolean truncated;

    @Schema(description = "是否对结果做了脱敏")
    private Boolean masked;

    @Schema(description = "是否走独立只读通道")
    private Boolean readonlyConnection;

    @Schema(description = "执行耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "S3 召回方式")
    private String recallMode;

    @Schema(description = "执行告警")
    private List<String> warnings = new ArrayList<>();

}
