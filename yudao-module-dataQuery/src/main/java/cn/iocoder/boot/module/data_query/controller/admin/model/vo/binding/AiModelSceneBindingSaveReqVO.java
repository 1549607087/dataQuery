package cn.iocoder.boot.module.data_query.controller.admin.model.vo.binding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - 模型场景绑定新增/修改 Request VO
 */
@Schema(description = "管理后台 - 模型场景绑定新增/修改 Request VO")
@Data
public class AiModelSceneBindingSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL_GEN")
    @NotEmpty(message = "场景编码不能为空")
    private String sceneCode;

    @Schema(description = "场景名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成")
    @NotEmpty(message = "场景名称不能为空")
    private String sceneName;

    @Schema(description = "主模型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "主模型编号不能为空")
    private Long primaryModelId;

    @Schema(description = "降级链，模型编号逗号分隔，按顺序尝试", example = "1,3")
    private String fallbackModelIds;

    @Schema(description = "路由策略：1 固定、2 优先级、3 权重、4 成本优先", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "路由策略不能为空")
    private Integer routeStrategy;

    @Schema(description = "关联提示词模板编号")
    private Long promptId;

    @Schema(description = "场景级温度覆盖，为空则用模型默认值")
    private BigDecimal temperatureOverride;

    @Schema(description = "是否启用：0 否、1 是", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "是否启用不能为空")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    // ========== 场景级覆盖：NULL 表示继承 ai_model_config 的模型级配置 ==========

    @Schema(description = "场景级超时覆盖（毫秒），为空则用模型默认", example = "8000")
    private Integer timeoutMsOverride;

    @Schema(description = "场景级 QPS 覆盖，为空则用模型默认，0 表示不限")
    private Integer qpsLimitOverride;

    @Schema(description = "场景级并发上限覆盖，为空则用模型默认，0 表示不限")
    private Integer concurrentLimitOverride;

    @Schema(description = "场景级重试次数覆盖，为空则用模型默认")
    private Integer retryCountOverride;

    // ========== 降级与灰度 ==========

    @Schema(description = "降级链是否强制跨供应商：0 否、1 是", example = "1")
    private Integer fallbackCrossProvider;

    @Schema(description = "单次请求最多降级跳数", example = "2")
    private Integer fallbackMaxDepth;

    @Schema(description = "场景级灰度比例 0-100，优先于模型级灰度比例")
    private Integer grayRatio;

    @Schema(description = "灰度稳定分桶维度：TENANT / USER / SESSION", example = "TENANT")
    private String grayBucketKey;

    @Schema(description = "当前生效的灰度实验编号，0 表示无实验")
    private Long grayExperimentId;

}
