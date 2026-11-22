package cn.iocoder.boot.module.data_query.framework.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 知识库 RAG 可调参数（P9.1）。
 *
 * 安全边界不在这里：租户私有表、base_id 归属校验、文件类型白名单与大小上限
 * 均由代码强制，配置只调「切片 / MQ / 上传上限 / 召回 / 问答运行」等参数。
 */
@Component
@ConfigurationProperties(prefix = "data-query.knowledge")
@Data
public class KnowledgeProperties {

    /** 单文件字节上限（MB），上传时强制校验 */
    private Integer maxFileSizeMb = 20;

    /** 允许上传的扩展名（大写），大小写不敏感校验 */
    private List<String> allowedFileTypes = Arrays.asList("TXT", "MD", "PDF", "DOC", "DOCX");

    /** 单个切片 token 上限（消费端 ChunkSplitter 使用） */
    private Integer chunkSize = 500;

    /**
     * 切片滑窗重叠占 chunkSize 的比例（默认 20%）。
     * 摄取时 overlap = round(chunkSize × ratio)；调整 chunk-size 时重叠自动跟随。
     */
    private Double chunkOverlapRatio = 0.2;

    /** Embedding 批量向量化每批条数（一次 HTTP 请求的文本数） */
    private Integer embeddingBatchSize = 32;

    // ==================== P9.2 知识库问答：检索参数 ====================

    /** 检索返回的切片数（裁剪后最多保留） */
    private Integer topK = 6;

    /** 向量路候选数（B-tree 预过滤后进入 RRF 的条数） */
    private Integer vectorCandidates = 20;

    /** 关键词路候选数 */
    private Integer keywordCandidates = 20;

    /** 关键词提取上限 */
    private Integer maxKeywords = 6;

    /** RRF 常数 K：score = Σ weight / (K + rank) */
    private Integer rrfK = 60;

    /** 向量路 RRF 权重 */
    private Double vectorWeight = 1.0;

    /** 关键词路 RRF 权重 */
    private Double keywordWeight = 1.0;

    /** RAG prompt 上下文硬预算（字符近似 token），超出按片裁剪 */
    private Integer contextTokenBudget = 6000;

    /** Embedding 不可用时是否允许关键词兜底 */
    private Boolean keywordFallbackEnabled = true;

    /** 向量命中条数低于该值时视为「不自信」，补跑关键词路 */
    private Integer keywordSupplementMinHits = 3;

    /**
     * 向量最近邻余弦距离大于该值时视为「不自信」，补跑关键词路。
     *
     * <p>余弦距离 = 1 - 相似度，取值 [0,2]；0.55 约等于相似度 0.45，属宽松阈值 ——
     * 宁可多跑一次关键词路，也不因阈值过严漏召回。
     */
    private Double keywordSupplementMaxDistance = 0.55;

    // ==================== P9.2 知识库问答：运行时参数 ====================

    /** 问题长度上限 */
    private Integer maxQuestionLength = 500;

    /** 模型回答落库最大字符数 */
    private Integer maxAnswerChars = 8000;

    /** 拼 history 时最多取多少条历史消息（默认 6 = 3 轮） */
    private Integer historyMaxMessages = 6;

    /** history 变量 Token 预算 */
    private Integer historyTokenBudget = 1500;

    /** RAG 模型调用超时（毫秒）；治理层默认 8s 对长回答偏短 */
    private Integer modelTimeoutMs = 60000;

    /** 是否开启提示词注入检测 */
    private Boolean promptInjectionGuardEnabled = true;

    // ==================== P9.3 知识库 SSE 流式问答 ====================

    /** 知识库问答流式开关（与智能问数的 chat.sse-enabled 解耦） */
    private Boolean sseEnabled = true;

    /**
     * SSE 单连接<b>绝对</b>超时（毫秒）。心跳不会续期——Tomcat 的 async timeout 没有 idle 语义，
     * 所以它必须按「最长回答所需时间」而不是「最大空闲时间」来配：
     * 30 字/s 出满 {@link #maxAnswerChars} 需要约 267s，故默认 5 分钟。
     * 真正卡死的流由 {@code stream-idle-ms} 负责结束，这里只是兜底上限。
     */
    private Integer sseTimeoutMs = 300000;

    /**
     * 流式首 token 超时（毫秒）。
     *
     * <p>与 {@code model-timeout-ms} 的区别：后者是「整次调用总时长」，逐 token 流式下用它会把
     * 长回答说到一半硬砍。首 token 前的失败客户端什么都没看到，仍可重试 / 跨供应商降级。
     */
    private Integer streamFirstTokenMs = 15000;

    /** 流式相邻增量间隔超时（毫秒）。已提交后超时只能终止，不重试（换模型会拼出混合文本）。 */
    private Integer streamIdleMs = 15000;

    // ==================== P9.3 表格 LLM 叙述化（摄取阶段，非问答热路径） ====================

    /** 单次表格叙述请求的输入 token 预算（超限按行分批） */
    private Integer tableNarrateTokenBudget = 2000;

    /** 单次表格叙述请求最多携带的表格行数 */
    private Integer tableNarrateBatchRows = 40;

    /** 表格叙述模型调用超时（毫秒）；治理层默认 8s 对表格描述偏短 */
    private Integer tableNarrateTimeoutMs = 30000;

    /** 相邻叙述段桥接比例：后一段开头重复前一段结尾的比例（20%） */
    private Double tableNarrateBridgeRatio = 0.2;

    /**
     * 单段叙述本体占 chunkSize 的比例，剩余留给桥接前缀。
     *
     * <p>默认 0.5（原 0.8）：叙述段需给前后讲解正文留出合并空间。占比过高时
     * 表格结论会独占一片，与正文讲解分离。
     */
    private Double tableNarrateSegmentRatio = 0.5;

    /** 单元格覆盖率下限：叙述文本未覆盖足够多的关键单元格值则视为幻觉，保留原表 */
    private Double tableNarrateMinCellCoverage = 0.8;

    /** 单张表最多送几批 LLM；超限放弃叙述保留原表，避免超大表卡死 MQ 消费 */
    private Integer tableNarrateMaxBatchesPerTable = 10;

    // ==================== P9.3b：Top-K 专用 Rerank 精排（问答热路径） ====================

    /**
     * 知识库召回 Top-K 的 Cross-Encoder 精排配置。
     *
     * <p>已固化启用：需先执行 nl2sql-rerank.sql 并在后台为 RERANK 场景绑定 model_type=3 模型；
     * 未绑定或任何调用失败都降级回 RRF 原序，不影响问答主链路。
     */
    private Rerank rerank = new Rerank();

    @Data
    public static class Rerank {
        /** 热路径硬超时（毫秒），须显著小于 model-timeout-ms；与模型配置的 timeout 取更小值 */
        private Integer timeoutMs = 1500;
        /** 连接超时（毫秒） */
        private Integer connectTimeoutMs = 2000;
        /** 协议：DASHSCOPE（默认，专用路径） / COHERE（/v1/rerank 兼容） */
        private String protocol = "DASHSCOPE";
        /** 覆盖默认 path；留空则用 protocol 对应的默认路径 */
        private String rerankPath = "";
        /** >0 时向 body 传 top_n；0 表示不传（返回全部 K 条分数，不扩大候选池） */
        private Integer topN = 0;
    }

    /** Broker 名称为 ${spring.application.name} 时 MQ Producer 组名的占位后缀不使用；这里只保留事务 Topic / Tag / 消费者组 */
    private Mq mq = new Mq();

    @Data
    public static class Mq {
        /** 是否启用 MQ 异步；关闭时上传走同步兜底（本次不实现消费，仅保留开关语义） */
        private Boolean enabled = true;

        /** gRPC Proxy 接入点（host:8081），多地址分号分隔；不是 nameServer */
        private String endpoints = "127.0.0.1:8080";

        /** 事务消息 Topic（需 message.type=TRANSACTION） */
        private String topic = "data-query-knowledge-doc-parse";

        /** 消息 Tag（预留 rebuild/delete 扩展） */
        private String tag = "parse";

        /** 消费者组（消费端后续步骤使用） */
        private String consumerGroup = "data-query-knowledge-doc-parse-consumer";

        /** 发送失败最大重试次数（gRPC Producer） */
        private Integer maxAttempts = 3;

        /** 消费线程数（gRPC PushConsumer） */
        private Integer consumeThreadCount = 4;

        /** 单条消息最大消费次数；超过后返回 SUCCESS 跳过（超出即人工介入，避免毒消息无限重投） */
        private Integer maxConsumeAttempts = 5;
    }
}
