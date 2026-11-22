package cn.iocoder.boot.module.data_query.framework.knowledge.rerank;

import lombok.Data;

/**
 * Rerank 调用结果。
 *
 * <p>scores 与入参 documents 严格同序同长；调用方按 index 归位后即可与候选切片对齐，
 * 无需关心供应商响应里 results 的返回顺序。
 */
@Data
public class RerankResult {

    /** 每个候选的相关性分数，下标与 documents 一一对应 */
    private double[] scores;

    /** 实际使用的模型代号（provider.code:modelId） */
    private String modelCode;

    /** 本次调用耗时（毫秒） */
    private int latencyMs;

    /** 供应商侧请求 ID，可空 */
    private String requestId;
}
