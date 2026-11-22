package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 提示词占位符校验 Response VO
 *
 * 只做「正文 {{var}} 与 ai_prompt_variable 定义」的一致性检查，不填充变量值。
 */
@Schema(description = "管理后台 - 提示词占位符校验 Response VO")
@Data
public class AiPromptValidateRespVO {

    @Schema(description = "是否通过校验", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean valid;

    @Schema(description = "正文中出现的占位符名")
    private List<String> contentVariables;

    @Schema(description = "变量定义表中已登记的占位符名")
    private List<String> declaredVariables;

    @Schema(description = "正文使用但未登记的占位符")
    private List<String> undeclaredVariables;

    @Schema(description = "已登记但正文未使用的占位符")
    private List<String> unusedVariables;

    @Schema(description = "已登记为必填但既无默认值也无来源键的变量")
    private List<String> suspiciousVariables;

}
