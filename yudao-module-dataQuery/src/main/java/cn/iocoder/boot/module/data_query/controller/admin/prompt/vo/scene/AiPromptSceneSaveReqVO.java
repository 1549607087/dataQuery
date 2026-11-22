package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 提示词场景字典新增/修改 Request VO
 *
 * 场景编码与 ai_model_scene_binding.scene_code 共用，新增场景时须同步维护模型侧绑定。
 */
@Schema(description = "管理后台 - 提示词场景字典新增/修改 Request VO")
@Data
public class AiPromptSceneSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL_GEN")
    @NotEmpty(message = "场景编码不能为空")
    private String sceneCode;

    @Schema(description = "场景名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成")
    @NotEmpty(message = "场景名称不能为空")
    private String sceneName;

    @Schema(description = "场景用途说明")
    private String description;

    @Schema(description = "默认占位符集合，JSON 数组，如 [\"question\",\"schema\"]")
    private String defaultVariables;

    @Schema(description = "建议模型类型：1 Chat、4 SQL 专用", example = "1")
    private Integer defaultModelType;

    @Schema(description = "状态：0 启用、1 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
