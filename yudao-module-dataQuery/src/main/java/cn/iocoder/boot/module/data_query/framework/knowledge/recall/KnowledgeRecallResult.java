package cn.iocoder.boot.module.data_query.framework.knowledge.recall;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库切片召回结果
 *
 * <p>包含召回方式、降级原因、候选数、裁剪后保留数、拼好的上下文正文（已带 [1..K] 编号）
 * 以及保留的命中项。召回为空不抛异常，由编排器转 CLARIFY 话术。
 */
@Data
public class KnowledgeRecallResult {

    public static final String MODE_VECTOR = "VECTOR";
    public static final String MODE_HYBRID = "HYBRID";
    public static final String MODE_KEYWORD_DEGRADED = "KEYWORD_DEGRADED";
    public static final String MODE_EMPTY = "EMPTY";

    private Long baseId;

    private String question;

    /** 召回方式：VECTOR / HYBRID / KEYWORD_DEGRADED / EMPTY */
    private String mode;

    /** 是否发生降级（向量路不可用或失败） */
    private Boolean degraded;

    private String degradeReason;

    private Integer vectorCandidateCount;

    private Integer keywordCandidateCount;

    /**
     * 关键词路是否被跳过（按需触发生效，向量召回已足够自信）。
     *
     * <p>此时 keywordCandidateCount 为 0 但 mode 为 VECTOR，属正常路径而非召回失败；
     * 排查时用它区分「没跑」和「跑了没命中」。
     */
    private Boolean keywordSkipped;

    /** 跳过关键词路的原因，仅 keywordSkipped=true 时有值 */
    private String keywordSkipReason;

    /** 裁剪后保留的切片数 */
    private Integer keptCount;

    /** 是否因 Token 预算被截断 */
    private Boolean truncated;

    private Integer tokenEstimate;

    /** 拼好的上下文正文，每条形如「【资料 1】文件名 | 第 n 页 | 标题路径\n内容」 */
    private String contextText;

    /** 本轮向量模型代号 */
    private String embeddingModelCode;

    /** 本轮是否实际执行了 Rerank 精排（null 视同 false） */
    private Boolean rerankApplied;

    /** Rerank 未执行 / 降级的原因（供检索调试台排查） */
    private String rerankSkipReason;

    /** 本轮 Rerank 模型代号；未配置为 PENDING */
    private String rerankModelCode;

    /** 本轮 Rerank 调用耗时（毫秒）；未执行为 0 */
    private Integer rerankLatencyMs;

    private Integer latencyMs;

    private List<KnowledgeChunkHit> items = new ArrayList<>();
}
