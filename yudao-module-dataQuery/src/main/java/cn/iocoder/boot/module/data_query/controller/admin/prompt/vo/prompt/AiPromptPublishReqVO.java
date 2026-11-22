package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 提示词发布 Request VO
 *
 * 发布会把当前正文、变量定义、参数打成版本快照，并把主表 version / currentVersionId 指向新版本。
 */
@Schema(description = "管理后台 - 提示词发布 Request VO")
@Data
public class AiPromptPublishReqVO {

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "提示词编号不能为空")
    private Long id;

    @Schema(description = "变更说明", example = "补充禁止 DDL 的约束")
    private String changeLog;

}
