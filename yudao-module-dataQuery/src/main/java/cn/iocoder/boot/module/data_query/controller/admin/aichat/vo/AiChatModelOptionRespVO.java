package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 对话页可选模型（下拉框）
 *
 * 数据来源是场景绑定的候选集，而不是全量启用模型：
 * 用户看到的就是真实可路由的模型，避免选了之后在问数阶段才报「不可用」。
 */
@Schema(description = "管理后台 - 对话页可选模型")
@Data
public class AiChatModelOptionRespVO {

    @Schema(description = "模型配置编号，作为 ask 的 modelConfigId 传回", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long modelConfigId;

    @Schema(description = "模型显示名称", example = "SQL 生成模型")
    private String name;

    @Schema(description = "模型标识", example = "qwen-plus")
    private String modelId;

    @Schema(description = "类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用")
    private Integer modelType;

    @Schema(description = "是否支持流式：0 否、1 是")
    private Integer supportStream;

    @Schema(description = "是否为当前场景的默认首选（不指定模型时走它）")
    private Boolean defaultModel;

}
