package cn.iocoder.boot.module.data_query.controller.admin.model.vo.candidate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 模型路由候选 Response VO
 */
@Schema(description = "管理后台 - 模型路由候选 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelRouteCandidateRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "场景绑定编号")
    @ExcelProperty("场景绑定编号")
    private Long bindingId;

    @Schema(description = "候选模型编号")
    @ExcelProperty("候选模型编号")
    private Long modelId;

    @Schema(description = "供应商编号")
    @ExcelProperty("供应商编号")
    private Long providerId;

    @Schema(description = "权重")
    @ExcelProperty("权重")
    private Integer weight;

    @Schema(description = "优先级")
    @ExcelProperty("优先级")
    private Integer priority;

    @Schema(description = "QPS 上限")
    @ExcelProperty("QPS 上限")
    private Integer maxQps;

    @Schema(description = "是否参与路由：0 否、1 是")
    @ExcelProperty("是否启用")
    private Integer enabled;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "租户编号")
    private Long tenantId;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
