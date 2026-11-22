package cn.iocoder.boot.module.data_query.controller.admin.model.vo.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 模型配置精简 Response VO（下拉框使用）
 */
@Schema(description = "管理后台 - 模型配置精简 Response VO")
@Data
public class AiModelConfigSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "模型显示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成模型")
    private String name;

    @Schema(description = "模型标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen-plus")
    private String modelId;

    @Schema(description = "类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用")
    private Integer modelType;

    @Schema(description = "是否支持流式：0 否、1 是")
    private Integer supportStream;

}
