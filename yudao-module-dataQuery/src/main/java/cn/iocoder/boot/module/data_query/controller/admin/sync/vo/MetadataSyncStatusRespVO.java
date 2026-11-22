package cn.iocoder.boot.module.data_query.controller.admin.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理后台 - 元数据同步状态 Response VO */
@Schema(description = "管理后台 - 元数据同步状态 Response VO")
@Data
public class MetadataSyncStatusRespVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasourceId;

    @Schema(description = "数据源同步状态：0 未同步、1 同步中、2 成功、3 失败")
    private Integer syncStatus;

    @Schema(description = "最近同步时间")
    private LocalDateTime lastSyncTime;

    @Schema(description = "当前结构版本号")
    private Integer structVersion;

    @Schema(description = "表总数 / 可见且可查表数 / 结构待确认表数")
    private Long tableCount;
    private Long visibleTableCount;
    private Long structChangedTableCount;

    @Schema(description = "列总数 / 可见且可查列数 / 结构待确认列数")
    private Long columnCount;
    private Long visibleColumnCount;
    private Long structChangedColumnCount;

    @Schema(description = "Join 路径数 / 已验证启用数")
    private Long relationCount;
    private Long verifiedRelationCount;

    @Schema(description = "向量行数 / 已构建 / 待构建（P2 接线前全部为待构建）")
    private Long vectorCount;
    private Long vectorBuiltCount;
    private Long vectorPendingCount;

}
