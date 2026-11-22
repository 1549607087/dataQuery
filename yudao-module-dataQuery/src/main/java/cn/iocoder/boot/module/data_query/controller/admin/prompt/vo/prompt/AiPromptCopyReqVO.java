package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 复制提示词模板 Request VO
 *
 * 用于租户基于平台内置模板复制一份自定义模板（source=3、parentId 指向原模板）。
 */
@Schema(description = "管理后台 - 复制提示词模板 Request VO")
@Data
public class AiPromptCopyReqVO {

    @Schema(description = "来源提示词编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "来源提示词编号不能为空")
    private Long id;

    @Schema(description = "新模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成·财务域")
    @NotEmpty(message = "新模板名称不能为空")
    private String name;

    @Schema(description = "新模板编码，租户内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROMPT_SQL_GEN_FINANCE")
    @NotEmpty(message = "新模板编码不能为空")
    private String code;

}
