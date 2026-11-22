package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 提示词模板新增/修改 Request VO
 *
 * variableDefs（变量快照）、version / currentVersionId / source / parentId 由服务端维护，
 * 不允许前端提交，避免绕过版本发布流程。
 */
@Schema(description = "管理后台 - 提示词模板新增/修改 Request VO")
@Data
public class AiPromptSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL_GEN")
    @NotEmpty(message = "场景编码不能为空")
    private String sceneCode;

    @Schema(description = "提示词名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成·默认模板")
    @NotEmpty(message = "提示词名称不能为空")
    private String name;

    @Schema(description = "提示词编码，租户内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROMPT_SQL_GEN_DEFAULT")
    @NotEmpty(message = "提示词编码不能为空")
    private String code;

    @Schema(description = "类型：1 System、2 User、3 完整模板", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "提示词类型不能为空")
    private Integer promptType;

    @Schema(description = "模板正文，占位符统一使用 {{var_name}}", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "模板正文不能为空")
    private String content;

    @Schema(description = "默认模型编号，0 表示跟随场景路由", example = "0")
    private Long modelId;

    @Schema(description = "模型参数覆盖 JSON：temperature / top_p / max_tokens")
    private String params;

    @Schema(description = "状态：0 启用、1 停用、2 灰度", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "灰度流量比例 0-100，仅 status=2 时生效", example = "0")
    private Integer grayRatio;

    @Schema(description = "同场景内优先级，数值越小越优先", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
