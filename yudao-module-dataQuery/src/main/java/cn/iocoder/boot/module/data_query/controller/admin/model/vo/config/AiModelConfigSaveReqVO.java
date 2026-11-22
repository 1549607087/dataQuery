package cn.iocoder.boot.module.data_query.controller.admin.model.vo.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - 模型配置新增/修改 Request VO
 *
 * healthStatus / lastCheckTime / lastCheckResult 由连通性检测写回，不允许前端提交。
 */
@Schema(description = "管理后台 - 模型配置新增/修改 Request VO")
@Data
public class AiModelConfigSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "供应商编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "供应商编号不能为空")
    private Long providerId;

    @Schema(description = "模型显示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "SQL 生成模型")
    @NotEmpty(message = "模型显示名称不能为空")
    private String name;

    @Schema(description = "模型标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen-plus")
    @NotEmpty(message = "模型标识不能为空")
    private String modelId;

    @Schema(description = "类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "模型类型不能为空")
    private Integer modelType;

    @Schema(description = "能力标签 JSON：streaming / function_call / json_mode / vision")
    private String capability;

    @Schema(description = "上下文窗口（token）")
    private Integer contextWindow;

    @Schema(description = "最大输出 token")
    private Integer maxOutputTokens;

    @Schema(description = "默认参数 JSON：temperature / top_p 等")
    private String defaultParams;

    @Schema(description = "是否支持流式：0 否、1 是")
    private Integer supportStream;

    @Schema(description = "输入单价（元 / 百万 token）")
    private BigDecimal inputPrice;

    @Schema(description = "输出单价（元 / 百万 token）")
    private BigDecimal outputPrice;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "QPS 上限，0 表示不限")
    private Integer qpsLimit;

    @Schema(description = "并发上限，0 表示不限")
    private Integer concurrentLimit;

    @Schema(description = "单次调用超时（毫秒）")
    private Integer timeoutMs;

    @Schema(description = "失败重试次数")
    private Integer retryCount;

    @Schema(description = "状态：0 启用、1 停用、2 灰度", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "灰度流量比例 0-100")
    private Integer grayRatio;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    // ========== 模型治理：熔断策略 ==========

    @Schema(description = "是否启用熔断：0 否、1 是", example = "0")
    private Integer circuitEnabled;

    @Schema(description = "熔断失败率阈值（百分比）", example = "50")
    private Integer circuitFailureRate;

    @Schema(description = "窗口内最小请求数，不足不熔断", example = "20")
    private Integer circuitMinSample;

    @Schema(description = "熔断统计窗口长度（秒）", example = "60")
    private Integer circuitWindowSeconds;

    @Schema(description = "OPEN 状态持续时长（秒）", example = "30")
    private Integer circuitOpenSeconds;

    @Schema(description = "HALF_OPEN 允许的探测请求数", example = "3")
    private Integer circuitHalfOpenProbes;

    @Schema(description = "熔断维度键，留空则运行时按 provider_id:model_config_id 自动生成")
    private String circuitKey;

    // ========== 模型治理：重试与退避 ==========

    @Schema(description = "可重试错误分类白名单，逗号分隔", example = "429,5xx,TIMEOUT,CONNECT")
    private String retryableErrors;

    @Schema(description = "指数退避基数（毫秒）", example = "200")
    private Integer retryBaseBackoffMs;

    @Schema(description = "单次退避上限（毫秒）", example = "2000")
    private Integer retryMaxBackoffMs;

    @Schema(description = "退避抖动系数 0.00-1.00", example = "0.30")
    private BigDecimal retryJitterRatio;

}
