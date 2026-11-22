package cn.iocoder.boot.module.data_query.controller.admin.vector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 管理后台 - Schema 向量索引构建结果 Response VO */
@Schema(description = "管理后台 - Schema 向量索引构建结果 Response VO")
@Data
public class SchemaVectorBuildRespVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasourceId;

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "是否全量重建")
    private Boolean rebuild;

    @Schema(description = "本租户 Embedding 是否已接线（P1 固定为 false，P2 接线后为 true）")
    private Boolean embeddingAvailable;

    @Schema(description = "Embedding 模型代号（参与 content_hash）")
    private String modelCode;

    @Schema(description = "向量维度，固定 1536")
    private Integer dimension;

    @Schema(description = "本次生成的 Document 总数")
    private Integer totalDocuments;

    @Schema(description = "新建 / 更新 / 内容未变跳过 / 下线数")
    private Integer created;
    private Integer updated;
    private Integer skipped;
    private Integer retired;

    @Schema(description = "本次真正生成向量数 / 待构建数")
    private Integer embedded;
    private Integer pendingEmbedding;

    @Schema(description = "数据来源的结构版本号")
    private Integer structVersion;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "任务状态：1 成功、2 部分成功、3 失败")
    private Integer status;

    @Schema(description = "错误摘要与告警")
    private List<String> warnings;

}
