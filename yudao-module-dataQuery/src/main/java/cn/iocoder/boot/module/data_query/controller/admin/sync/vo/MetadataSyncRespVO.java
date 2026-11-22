package cn.iocoder.boot.module.data_query.controller.admin.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 管理后台 - 元数据同步结果 Response VO */
@Schema(description = "管理后台 - 元数据同步结果 Response VO")
@Data
public class MetadataSyncRespVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasourceId;

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "任务类型：2 全量同步、3 增量同步")
    private Integer syncType;

    @Schema(description = "是否成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    @Schema(description = "同步日志状态：1 成功、2 部分成功、3 失败")
    private Integer status;

    @Schema(description = "新增表 / 变更表 / 失效表数")
    private Integer tableAdded;
    private Integer tableChanged;
    private Integer tableRemoved;

    @Schema(description = "新增列 / 变更列 / 失效列数")
    private Integer columnAdded;
    private Integer columnChanged;
    private Integer columnRemoved;

    @Schema(description = "外键自动推断新增的 Join 路径数（默认未启用，需人工验证）")
    private Integer relationAdded;

    @Schema(description = "同步前后结构版本号")
    private Integer structVersionBefore;
    private Integer structVersionAfter;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "错误摘要；失败时含异常类名，数据库本地化乱码原文已剔除")
    private String errorMsg;

    @Schema(description = "变更明细与非阻断告警")
    private List<String> warnings;

    @Schema(description = "是否走了只读连接通道")
    private Boolean readonlyConnection;

}
