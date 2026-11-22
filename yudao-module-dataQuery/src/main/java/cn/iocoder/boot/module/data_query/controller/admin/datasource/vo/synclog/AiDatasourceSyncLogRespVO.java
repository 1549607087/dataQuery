package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.synclog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 数据源同步日志 Response VO
 */
@Schema(description = "管理后台 - 数据源同步日志 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiDatasourceSyncLogRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_datasource.id")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "任务批次号，同一批次的多条明细共用")
    @ExcelProperty("任务批次号")
    private String taskNo;

    @Schema(description = "任务类型：1 连通性检测、2 全量元数据同步、3 增量元数据同步、4 结构变更检测、5 数据预览采样、6 枚举值采集")
    @ExcelProperty("任务类型")
    private Integer syncType;

    @Schema(description = "触发方式：1 手动、2 定时、3 创建后自动、4 结构变更告警")
    @ExcelProperty("触发方式")
    private Integer triggerType;

    @Schema(description = "本次任务范围：schema / 表清单")
    @ExcelProperty("本次任务范围")
    private String scope;

    @Schema(description = "开始时间")
    @ExcelProperty("开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    @ExcelProperty("结束时间")
    private LocalDateTime endTime;

    @Schema(description = "耗时（毫秒）")
    @ExcelProperty("耗时（毫秒）")
    private Integer latencyMs;

    @Schema(description = "状态：0 进行中、1 成功、2 部分成功、3 失败")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "本次新增表数")
    @ExcelProperty("本次新增表数")
    private Integer tableAdded;

    @Schema(description = "本次结构变更表数")
    @ExcelProperty("本次结构变更表数")
    private Integer tableChanged;

    @Schema(description = "本次源端删除表数")
    @ExcelProperty("本次源端删除表数")
    private Integer tableRemoved;

    @Schema(description = "本次新增字段数")
    @ExcelProperty("本次新增字段数")
    private Integer columnAdded;

    @Schema(description = "本次变更字段数")
    @ExcelProperty("本次变更字段数")
    private Integer columnChanged;

    @Schema(description = "本次删除字段数")
    @ExcelProperty("本次删除字段数")
    private Integer columnRemoved;

    @Schema(description = "本次新增 Join 路径数")
    @ExcelProperty("本次新增 Join 路径数")
    private Integer relationAdded;

    @Schema(description = "本次新增枚举映射数")
    @ExcelProperty("本次新增枚举映射数")
    private Integer enumAdded;

    @Schema(description = "结构变更明细 JSON：表名/字段名/变更前后类型，管理端变更告警的数据来源")
    @ExcelProperty("结构变更明细 JSON")
    private String changeDetail;

    @Schema(description = "任务前结构版本号")
    @ExcelProperty("任务前结构版本号")
    private Integer structVersionBefore;

    @Schema(description = "任务后结构版本号")
    @ExcelProperty("任务后结构版本号")
    private Integer structVersionAfter;

    @Schema(description = "错误码")
    @ExcelProperty("错误码")
    private String errorCode;

    @Schema(description = "错误摘要，禁止记录完整密码与连接串")
    @ExcelProperty("错误摘要")
    private String errorMsg;

    @Schema(description = "操作人用户 ID，定时任务为 0")
    @ExcelProperty("操作人用户 ID")
    private Long operatorId;

    @Schema(description = "操作人名称")
    @ExcelProperty("操作人名称")
    private String operatorName;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;


}
