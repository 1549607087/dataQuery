package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 批量保存提示词模板变量 Request VO
 *
 * 编辑态整包覆盖：逻辑删除该提示词下的旧变量后写入新列表，并回写 ai_prompt.variable_defs 只读快照。
 */
@Schema(description = "管理后台 - 批量保存提示词模板变量 Request VO")
@Data
public class AiPromptVariableBatchSaveReqVO {

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "提示词编号不能为空")
    private Long promptId;

    @Schema(description = "场景编码，子项未填写时统一使用", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "变量定义列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "变量定义列表不能为空")
    @Valid
    private List<AiPromptVariableSaveReqVO> variables;

}
