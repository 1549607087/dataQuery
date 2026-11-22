package cn.iocoder.boot.module.data_query.controller.admin.model.vo.candidate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 模型路由候选新增/修改 Request VO
 */
@Schema(description = "管理后台 - 模型路由候选新增/修改 Request VO")
@Data
public class AiModelRouteCandidateSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "场景绑定编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "场景绑定编号不能为空")
    private Long bindingId;

    @Schema(description = "候选模型编号，关联 ai_model_config.id", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "候选模型编号不能为空")
    private Long modelId;

    @Schema(description = "供应商编号快照，用于跨供应商降级校验", example = "1")
    private Long providerId;

    @Schema(description = "权重（策略 3 用），相对值，运行时归一化", example = "100")
    private Integer weight;

    @Schema(description = "优先级（策略 2 用），数值越小越优先", example = "1")
    private Integer priority;

    @Schema(description = "该候选单独可承载的 QPS 上限，0 表示不限")
    private Integer maxQps;

    @Schema(description = "是否参与路由：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
