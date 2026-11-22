package cn.iocoder.boot.module.data_query.controller.admin.model.vo.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 模型配置 Response VO
 */
@Schema(description = "管理后台 - 模型配置 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelConfigRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "供应商编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("供应商编号")
    private Long providerId;

    @Schema(description = "模型显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("模型显示名称")
    private String name;

    @Schema(description = "模型标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("模型标识")
    private String modelId;

    @Schema(description = "类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用")
    @ExcelProperty("模型类型")
    private Integer modelType;

    @Schema(description = "能力标签 JSON")
    private String capability;

    @Schema(description = "上下文窗口（token）")
    @ExcelProperty("上下文窗口")
    private Integer contextWindow;

    @Schema(description = "最大输出 token")
    @ExcelProperty("最大输出 token")
    private Integer maxOutputTokens;

    @Schema(description = "默认参数 JSON")
    private String defaultParams;

    @Schema(description = "是否支持流式：0 否、1 是")
    @ExcelProperty("支持流式")
    private Integer supportStream;

    @Schema(description = "输入单价（元 / 百万 token）")
    @ExcelProperty("输入单价")
    private BigDecimal inputPrice;

    @Schema(description = "输出单价（元 / 百万 token）")
    @ExcelProperty("输出单价")
    private BigDecimal outputPrice;

    @Schema(description = "币种")
    @ExcelProperty("币种")
    private String currency;

    @Schema(description = "QPS 上限，0 表示不限")
    @ExcelProperty("QPS 上限")
    private Integer qpsLimit;

    @Schema(description = "并发上限，0 表示不限")
    @ExcelProperty("并发上限")
    private Integer concurrentLimit;

    @Schema(description = "单次调用超时（毫秒）")
    @ExcelProperty("超时（毫秒）")
    private Integer timeoutMs;

    @Schema(description = "失败重试次数")
    @ExcelProperty("重试次数")
    private Integer retryCount;

    @Schema(description = "状态：0 启用、1 停用、2 灰度")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "灰度流量比例 0-100")
    @ExcelProperty("灰度比例")
    private Integer grayRatio;

    @Schema(description = "健康状态：0 未检测、1 健康、2 异常")
    @ExcelProperty("健康状态")
    private Integer healthStatus;

    @Schema(description = "最近一次连通性检测时间")
    @ExcelProperty("最近检测时间")
    private LocalDateTime lastCheckTime;

    @Schema(description = "最近一次检测结果")
    private String lastCheckResult;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    // ========== 模型治理：熔断策略 ==========

    @Schema(description = "是否启用熔断：0 否、1 是")
    @ExcelProperty("启用熔断")
    private Integer circuitEnabled;

    @Schema(description = "熔断失败率阈值（百分比）")
    @ExcelProperty("熔断失败率阈值")
    private Integer circuitFailureRate;

    @Schema(description = "窗口内最小请求数")
    @ExcelProperty("熔断最小样本")
    private Integer circuitMinSample;

    @Schema(description = "熔断统计窗口长度（秒）")
    @ExcelProperty("熔断窗口秒数")
    private Integer circuitWindowSeconds;

    @Schema(description = "OPEN 状态持续时长（秒）")
    @ExcelProperty("熔断打开秒数")
    private Integer circuitOpenSeconds;

    @Schema(description = "HALF_OPEN 允许的探测请求数")
    @ExcelProperty("半开探测数")
    private Integer circuitHalfOpenProbes;

    @Schema(description = "熔断维度键")
    private String circuitKey;

    // ========== 模型治理：重试与退避 ==========

    @Schema(description = "可重试错误分类白名单")
    private String retryableErrors;

    @Schema(description = "指数退避基数（毫秒）")
    @ExcelProperty("退避基数")
    private Integer retryBaseBackoffMs;

    @Schema(description = "单次退避上限（毫秒）")
    @ExcelProperty("退避上限")
    private Integer retryMaxBackoffMs;

    @Schema(description = "退避抖动系数")
    @ExcelProperty("抖动系数")
    private BigDecimal retryJitterRatio;

}
