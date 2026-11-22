package cn.iocoder.boot.module.data_query.controller.admin.model.vo.governance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台 - 路由预览 Response VO
 *
 * 不发起任何 LLM 调用，只返回「按当前配置这次请求会怎么走」，
 * 用来验证启停 / 灰度 / 权重 / 成本优先的修改是否即时生效。
 */
@Schema(description = "管理后台 - 路由预览 Response VO")
@Data
public class ModelRoutePreviewRespVO {

    @Schema(description = "场景编码")
    private String sceneCode;

    @Schema(description = "实际路由策略：1 固定、2 优先级、3 权重、4 成本优先")
    private Integer routeStrategy;

    @Schema(description = "候选是否来自 ai_model_route_candidate；false 表示回落解析旧降级链字符串")
    private Boolean fromCandidateTable;

    @Schema(description = "是否命中灰度")
    private Boolean gray;

    @Schema(description = "灰度分组 A / B")
    private String grayGroup;

    @Schema(description = "灰度实验编号，0 表示无实验")
    private Long grayExperimentId;

    @Schema(description = "分桶维度")
    private String grayBucketKey;

    @Schema(description = "分桶输入值快照")
    private String bucketValue;

    @Schema(description = "PROMPT 灰度实验覆盖的提示词编号，0 表示不覆盖")
    private Long promptIdOverride;

    @Schema(description = "按策略排好序的候选与降级链")
    private List<CandidateVO> candidates;

    @Schema(description = "主模型本次生效的治理参数")
    private EffectiveVO effective;

    @Schema(description = "模型路由候选预览")
    @Data
    public static class CandidateVO {

        @Schema(description = "模型配置编号")
        private Long modelConfigId;

        @Schema(description = "模型标识")
        private String modelId;

        @Schema(description = "模型显示名称")
        private String name;

        @Schema(description = "供应商编号")
        private Long providerId;

        @Schema(description = "状态：0 启用、2 灰度")
        private Integer status;

        @Schema(description = "输入单价（元 / 百万 token）")
        private BigDecimal inputPrice;

        @Schema(description = "输出单价（元 / 百万 token）")
        private BigDecimal outputPrice;

        @Schema(description = "是否启用熔断")
        private Integer circuitEnabled;

        @Schema(description = "熔断维度键")
        private String circuitKey;

    }

    @Schema(description = "主模型生效的治理参数")
    @Data
    public static class EffectiveVO {

        @Schema(description = "单次调用超时（毫秒）")
        private Integer timeoutMs;

        @Schema(description = "QPS 上限，0 表示不限")
        private Integer qpsLimit;

        @Schema(description = "并发上限，0 表示不限")
        private Integer concurrentLimit;

        @Schema(description = "重试次数")
        private Integer retryCount;

        @Schema(description = "可重试错误分类白名单")
        private String retryableErrors;

        @Schema(description = "退避基数（毫秒）")
        private Integer retryBaseBackoffMs;

        @Schema(description = "退避上限（毫秒）")
        private Integer retryMaxBackoffMs;

        @Schema(description = "退避抖动系数")
        private BigDecimal retryJitterRatio;

        @Schema(description = "最大降级跳数")
        private Integer fallbackMaxDepth;

        @Schema(description = "是否强制跨供应商降级")
        private Integer fallbackCrossProvider;

    }

}
