package cn.iocoder.boot.module.data_query.controller.admin.model.vo.experiment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 灰度实验新增/修改 Request VO
 */
@Schema(description = "管理后台 - 灰度实验新增/修改 Request VO")
@Data
public class AiModelGrayExperimentSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "实验名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL_GEN 换模型灰度")
    @NotEmpty(message = "实验名称不能为空")
    private String name;

    @Schema(description = "灰度变量：MODEL / PROMPT，一次实验只允许一个变量", requiredMode = Schema.RequiredMode.REQUIRED, example = "MODEL")
    @NotEmpty(message = "灰度变量不能为空")
    private String grayObject;

    @Schema(description = "生效场景编码", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "场景绑定编号")
    private Long bindingId;

    @Schema(description = "对照组 A 模型编号")
    private Long baselineModelId;

    @Schema(description = "灰度组 B 模型编号")
    private Long grayModelId;

    @Schema(description = "对照组 A 提示词编号（gray_object=PROMPT 时用）")
    private Long baselinePromptId;

    @Schema(description = "灰度组 B 提示词编号（gray_object=PROMPT 时用）")
    private Long grayPromptId;

    @Schema(description = "灰度组流量比例 0-100，建议从 5 起步", example = "5")
    private Integer grayRatio;

    @Schema(description = "稳定分桶维度：TENANT / USER / SESSION", example = "TENANT")
    private String bucketKey;

    @Schema(description = "实验开始时间")
    private LocalDateTime startTime;

    @Schema(description = "实验结束时间")
    private LocalDateTime endTime;

    @Schema(description = "每组最小样本量", example = "1000")
    private Integer minSampleSize;

    @Schema(description = "观察指标 JSON")
    private String metrics;

    @Schema(description = "状态：0 草稿、1 进行中、2 已结束、3 已全量、4 已回滚")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "实验结论")
    private String conclusion;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
