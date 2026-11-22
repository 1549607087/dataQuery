package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 提示词模板精简 Response VO（下拉框使用）
 */
@Schema(description = "管理后台 - 提示词模板精简 Response VO")
@Data
public class AiPromptSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "提示词名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "提示词编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "场景编码")
    private String sceneCode;

    @Schema(description = "状态：0 启用、1 停用、2 灰度")
    private Integer status;

    @Schema(description = "当前版本号")
    private Integer version;

}
