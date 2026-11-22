package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.table;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 数据源表元数据 Response VO
 */
@Schema(description = "管理后台 - 数据源表元数据 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiDatasourceTableRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_datasource.id")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "所属 schema，冗余存储以支持同一数据源多 schema")
    @ExcelProperty("所属 schema")
    private String schemaName;

    @Schema(description = "物理表名")
    @ExcelProperty("物理表名")
    private String tableName;

    @Schema(description = "对象类型：1 表、2 视图、3 物化视图、4 外部表")
    @ExcelProperty("对象类型")
    private Integer tableType;

    @Schema(description = "业务中文名，喂给 LLM 的 Schema 描述")
    @ExcelProperty("业务中文名")
    private String nameZh;

    @Schema(description = "业务描述与统计注意事项，Schema 召回时注入提示词")
    @ExcelProperty("业务描述与统计注意事项")
    private String description;

    @Schema(description = "表角色：1 事实表、2 维表、3 桥表、4 字典/配置表、5 其它")
    @ExcelProperty("表角色")
    private Integer tableRole;

    @Schema(description = "主键列名，JSON 数组")
    @ExcelProperty("主键列名")
    private String primaryKey;

    @Schema(description = "默认时间字段，用于「本月 / 近 7 天」等时间口径解析")
    @ExcelProperty("默认时间字段")
    private String defaultTimeColumn;

    @Schema(description = "行数估算，用于 Explain 成本预估与 SQL 生成提示")
    @ExcelProperty("行数估算")
    private Long rowCountEstimate;

    @Schema(description = "字段数，元数据同步写入")
    @ExcelProperty("字段数")
    private Integer columnCount;

    @Schema(description = "数据预览采样结果快照 JSON（已脱敏，最多 N 行）")
    @ExcelProperty("数据预览采样结果快照 JSO")
    private String sampleRows;

    @Schema(description = "最近一次采样时间")
    @ExcelProperty("最近一次采样时间")
    private LocalDateTime sampleTime;

    @Schema(description = "管理端是否可见：0 否、1 是")
    @ExcelProperty("管理端是否可见")
    private Integer visible;

    @Schema(description = "是否允许 LLM 生成 SQL 时引用：0 否、1 是；静态校验的表白名单")
    @ExcelProperty("是否允许 LLM 生成 SQ")
    private Integer queryable;

    @Schema(description = "整表敏感级别：0 无、1 内部、2 敏感、3 高敏")
    @ExcelProperty("整表敏感级别")
    private Integer sensitiveLevel;

    @Schema(description = "同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除（失效）")
    @ExcelProperty("同步状态")
    private Integer syncStatus;

    @Schema(description = "结构指纹（列名+类型+约束的哈希），用于结构变更检测")
    @ExcelProperty("结构指纹（列名+类型+约束的")
    private String structHash;

    @Schema(description = "最近一次该表的同步时间")
    @ExcelProperty("最近一次该表的同步时间")
    private LocalDateTime lastSyncTime;

    @Schema(description = "Schema 召回时的排序权重")
    @ExcelProperty("Schema 召回时的排序权")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;


}
