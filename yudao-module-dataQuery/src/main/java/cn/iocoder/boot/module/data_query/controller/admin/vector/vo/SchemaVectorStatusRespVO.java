package cn.iocoder.boot.module.data_query.controller.admin.vector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理后台 - Schema 向量索引状态 Response VO */
@Schema(description = "管理后台 - Schema 向量索引状态 Response VO")
@Data
public class SchemaVectorStatusRespVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasourceId;

    @Schema(description = "本租户 Embedding 是否已接线", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean embeddingAvailable;

    @Schema(description = "当前 Embedding 模型代号")
    private String modelCode;

    @Schema(description = "向量维度")
    private Integer dimension;

    @Schema(description = "有效向量行数 / 已构建 / 待构建 / 已下线")
    private Long vectorCount;
    private Long builtCount;
    private Long pendingCount;
    private Long retiredCount;

    @Schema(description = "struct_version 落后于数据源的行数（索引新鲜度）")
    private Long staleCount;

    @Schema(description = "数据源当前结构版本号")
    private Integer structVersion;

    @Schema(description = "最近一次构建任务编号 / 时间 / 状态")
    private String lastTaskNo;
    private LocalDateTime lastBuildTime;
    private Integer lastBuildStatus;

    @Schema(description = "口径与风险提示")
    private List<String> notes;

}
