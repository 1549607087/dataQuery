package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 提示词场景精简 Response VO（下拉框使用）
 */
@Schema(description = "管理后台 - 提示词场景精简 Response VO")
@Data
public class AiPromptSceneSimpleRespVO {

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "场景名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成")
    private String sceneName;

    @Schema(description = "默认占位符集合 JSON 数组")
    private String defaultVariables;

    @Schema(description = "建议模型类型：1 Chat、4 SQL 专用")
    private Integer defaultModelType;

}
