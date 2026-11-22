package cn.iocoder.boot.module.data_query.controller.admin.model.vo.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 模型调用日志 Response VO
 */
@Schema(description = "管理后台 - 模型调用日志 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelCallLogRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "全链路追踪 ID")
    @ExcelProperty("链路 ID")
    private String traceId;

    @Schema(description = "会话编号")
    @ExcelProperty("会话编号")
    private Long conversationId;

    @Schema(description = "场景编码")
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "供应商编号")
    @ExcelProperty("供应商编号")
    private Long providerId;

    @Schema(description = "模型配置编号")
    @ExcelProperty("模型配置编号")
    private Long modelConfigId;

    @Schema(description = "模型标识快照")
    @ExcelProperty("模型标识")
    private String modelId;

    @Schema(description = "请求 Token 数")
    @ExcelProperty("请求 Token")
    private Integer requestTokens;

    @Schema(description = "响应 Token 数")
    @ExcelProperty("响应 Token")
    private Integer responseTokens;

    @Schema(description = "总 Token 数")
    @ExcelProperty("总 Token")
    private Integer totalTokens;

    @Schema(description = "本次调用费用")
    @ExcelProperty("费用")
    private BigDecimal cost;

    @Schema(description = "耗时（毫秒）")
    @ExcelProperty("耗时（毫秒）")
    private Integer latencyMs;

    @Schema(description = "状态：0 成功、1 失败、2 超时、3 降级")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "降级前的模型编号")
    @ExcelProperty("降级前模型编号")
    private Long fallbackFromModelId;

    @Schema(description = "错误码")
    @ExcelProperty("错误码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "用户编号")
    @ExcelProperty("用户编号")
    private Long userId;

    @Schema(description = "租户编号")
    @ExcelProperty("租户编号")
    private Long tenantId;

    @Schema(description = "调用时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("调用时间")
    private LocalDateTime createTime;

    // ========== 灰度 A/B 埋点 ==========

    @Schema(description = "是否命中灰度流量：0 否、1 是")
    @ExcelProperty("命中灰度")
    private Integer isGray;

    @Schema(description = "灰度分组：A 对照组、B 灰度组")
    @ExcelProperty("灰度分组")
    private String grayGroup;

    @Schema(description = "灰度实验编号，0 表示非实验流量")
    @ExcelProperty("灰度实验编号")
    private Long grayExperimentId;

    @Schema(description = "本次实际使用的分桶维度快照")
    private String grayBucketKey;

    // ========== 重试 / 降级 / 路由归因 ==========

    @Schema(description = "重试或降级指向的原始链路 ID，首次调用留空；成本按此字段聚合")
    @ExcelProperty("原始链路 ID")
    private String retryOfTraceId;

    @Schema(description = "第几次重试，0 = 首次调用")
    @ExcelProperty("重试序号")
    private Integer retryIndex;

    @Schema(description = "降级链第几跳，0 = 未降级")
    @ExcelProperty("降级深度")
    private Integer fallbackDepth;

    @Schema(description = "本次实际路由策略：1 固定、2 优先级、3 权重、4 成本优先")
    @ExcelProperty("路由策略")
    private Integer routeStrategy;

    // ========== 熔断与错误分类 ==========

    @Schema(description = "调用发生时的熔断状态：CLOSED / OPEN / HALF_OPEN")
    @ExcelProperty("熔断状态")
    private String circuitState;

    @Schema(description = "熔断维度键快照")
    private String circuitKey;

    @Schema(description = "失败细分类：RATE_LIMIT / SERVER_ERROR / TIMEOUT / CONNECT / AUTH / BAD_REQUEST / CONTENT_FILTER / INVALID_OUTPUT / CIRCUIT_OPEN")
    @ExcelProperty("错误分类")
    private String errorCategory;

    // ========== 可观测 ==========

    @Schema(description = "进入模型客户端前的排队等待时长（毫秒）")
    @ExcelProperty("排队耗时")
    private Integer queueWaitMs;

    @Schema(description = "供应商侧请求 ID，用于对账与重复扣费排查")
    private String requestId;

}
