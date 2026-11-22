package cn.iocoder.boot.module.data_query.controller.admin.model.vo.experiment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 灰度实验 Response VO
 */
@Schema(description = "管理后台 - 灰度实验 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelGrayExperimentRespVO {

    @Schema(description = "编号")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "实验名称")
    @ExcelProperty("实验名称")
    private String name;

    @Schema(description = "灰度变量：MODEL / PROMPT")
    @ExcelProperty("灰度变量")
    private String grayObject;

    @Schema(description = "生效场景编码")
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "场景绑定编号")
    @ExcelProperty("场景绑定编号")
    private Long bindingId;

    @Schema(description = "对照组 A 模型编号")
    @ExcelProperty("对照组模型")
    private Long baselineModelId;

    @Schema(description = "灰度组 B 模型编号")
    @ExcelProperty("灰度组模型")
    private Long grayModelId;

    @Schema(description = "对照组 A 提示词编号")
    private Long baselinePromptId;

    @Schema(description = "灰度组 B 提示词编号")
    private Long grayPromptId;

    @Schema(description = "灰度组流量比例 0-100")
    @ExcelProperty("灰度比例")
    private Integer grayRatio;

    @Schema(description = "稳定分桶维度")
    @ExcelProperty("分桶维度")
    private String bucketKey;

    @Schema(description = "实验开始时间")
    @ExcelProperty("开始时间")
    private LocalDateTime startTime;

    @Schema(description = "实验结束时间")
    @ExcelProperty("结束时间")
    private LocalDateTime endTime;

    @Schema(description = "每组最小样本量")
    @ExcelProperty("最小样本量")
    private Integer minSampleSize;

    @Schema(description = "观察指标 JSON")
    private String metrics;

    @Schema(description = "状态：0 草稿、1 进行中、2 已结束、3 已全量、4 已回滚")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "实验结论")
    private String conclusion;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "租户编号")
    private Long tenantId;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
