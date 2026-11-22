package cn.iocoder.boot.module.data_query.controller.admin.model.vo.binding;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 模型场景绑定 Response VO
 */
@Schema(description = "管理后台 - 模型场景绑定 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelSceneBindingRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "场景名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("场景名称")
    private String sceneName;

    @Schema(description = "主模型编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("主模型编号")
    private Long primaryModelId;

    @Schema(description = "降级链，模型编号逗号分隔")
    @ExcelProperty("降级链")
    private String fallbackModelIds;

    @Schema(description = "路由策略：1 固定、2 优先级、3 权重、4 成本优先")
    @ExcelProperty("路由策略")
    private Integer routeStrategy;

    @Schema(description = "关联提示词模板编号")
    @ExcelProperty("提示词编号")
    private Long promptId;

    @Schema(description = "场景级温度覆盖")
    @ExcelProperty("温度覆盖")
    private BigDecimal temperatureOverride;

    @Schema(description = "是否启用：0 否、1 是")
    @ExcelProperty("是否启用")
    private Integer enabled;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    // ========== 场景级覆盖 ==========

    @Schema(description = "场景级超时覆盖（毫秒），为空表示继承模型级")
    @ExcelProperty("超时覆盖")
    private Integer timeoutMsOverride;

    @Schema(description = "场景级 QPS 覆盖")
    @ExcelProperty("QPS 覆盖")
    private Integer qpsLimitOverride;

    @Schema(description = "场景级并发上限覆盖")
    @ExcelProperty("并发覆盖")
    private Integer concurrentLimitOverride;

    @Schema(description = "场景级重试次数覆盖")
    @ExcelProperty("重试覆盖")
    private Integer retryCountOverride;

    // ========== 降级与灰度 ==========

    @Schema(description = "降级链是否强制跨供应商")
    @ExcelProperty("跨供应商降级")
    private Integer fallbackCrossProvider;

    @Schema(description = "单次请求最多降级跳数")
    @ExcelProperty("最大降级跳数")
    private Integer fallbackMaxDepth;

    @Schema(description = "场景级灰度比例 0-100")
    @ExcelProperty("灰度比例")
    private Integer grayRatio;

    @Schema(description = "灰度稳定分桶维度")
    @ExcelProperty("灰度分桶维度")
    private String grayBucketKey;

    @Schema(description = "当前生效的灰度实验编号，0 表示无实验")
    @ExcelProperty("灰度实验编号")
    private Long grayExperimentId;

}
