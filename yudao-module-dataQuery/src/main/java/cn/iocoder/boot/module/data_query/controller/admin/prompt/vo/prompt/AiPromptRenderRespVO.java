package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 提示词渲染调试 Response VO
 */
@Schema(description = "管理后台 - 提示词渲染调试 Response VO")
@Data
public class AiPromptRenderRespVO {

    @Schema(description = "渲染后的正文；校验接口返回原文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "正文中出现的全部占位符名")
    private List<String> variables;

    @Schema(description = "正文使用但未在变量定义表中登记的占位符")
    private List<String> undeclaredVariables;

    @Schema(description = "已登记但正文未使用的占位符")
    private List<String> unusedVariables;

    @Schema(description = "必填但未提供值且无默认值的占位符")
    private List<String> missingVariables;

    @Schema(description = "是否通过校验（无未登记占位符、无缺失必填值）")
    private Boolean valid;

    @Schema(description = "Token 数量预估值（按字符数 / 4 粗估）")
    private Integer tokenEstimate;

}
