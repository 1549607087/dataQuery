package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 提示词版本回滚 Request VO
 *
 * 回滚不修改历史版本，而是基于目标版本内容生成一个新版本，保证版本号单调递增、历史可追溯。
 */
@Schema(description = "管理后台 - 提示词版本回滚 Request VO")
@Data
public class AiPromptRollbackReqVO {

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "提示词编号不能为空")
    private Long promptId;

    @Schema(description = "要回滚到的版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "目标版本编号不能为空")
    private Long versionId;

    @Schema(description = "变更说明", example = "回滚至 v2")
    private String changeLog;

}
