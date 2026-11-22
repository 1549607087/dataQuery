package cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台 - 一键导入预览 Response VO
 *
 * 只读扫描目标库结构，绝不回显连接串、账号或密码，也不产生任何落库副作用。
 */
@Schema(description = "管理后台 - 一键导入预览 Response VO")
@Data
public class MetadataPreviewRespVO {

    @Schema(description = "是否扫描成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    @Schema(description = "已有数据源模式下的数据源编号；新建模式预览时为 null")
    private Long datasourceId;

    @Schema(description = "SQL 方言")
    private String dialect;

    @Schema(description = "扫描使用的 schema")
    private String schemaName;

    @Schema(description = "将导入的表总数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer tableCount;

    @Schema(description = "将导入的字段总数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer columnCount;

    @Schema(description = "外键推断出的 Join 候选数（默认未启用，需人工验证）")
    private Integer relationCount;

    @Schema(description = "单次导入允许的最大表数（超过则拒绝导入，防止误扫整库）")
    private Integer maxTables;

    @Schema(description = "预览响应中最多回传多少张表的明细")
    private Integer maxPreviewTables;

    @Schema(description = "表明细是否被预览上限截断（总数仍按真实值返回）")
    private Boolean truncated;

    @Schema(description = "表级明细（最多 maxPreviewTables 张）")
    private List<TableItem> tables = new ArrayList<>();

    @Schema(description = "采集范围说明与非阻断告警")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "扫描失败原因（已脱敏）")
    private String errorMsg;

    @Schema(description = "管理后台 - 预览表项")
    @Data
    public static class TableItem {

        @Schema(description = "schema 名")
        private String schemaName;

        @Schema(description = "表名")
        private String tableName;

        @Schema(description = "表中文名（来自数据库注释，可为空）")
        private String tableNameZh;

        @Schema(description = "1 物理表 / 2 视图")
        private Integer tableType;

        @Schema(description = "字段总数")
        private Integer columnCount;

        @Schema(description = "主键字段")
        private List<String> primaryKeys = new ArrayList<>();

        @Schema(description = "字段名样例（最多 maxColumnsPerPreviewTable 个）")
        private List<String> columns = new ArrayList<>();

        @Schema(description = "字段中文名样例（来自数据库注释，与 columns 一一对应，可为空）")
        private List<String> columnNameZhs = new ArrayList<>();

        @Schema(description = "字段样例是否被截断")
        private Boolean columnsTruncated;

    }

}
