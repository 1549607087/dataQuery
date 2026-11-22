package cn.iocoder.boot.module.data_query.framework.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * P5 问数编排器可调参数。
 *
 * 注意：安全红线不在这里——数据源归属、租户固化、S3 服务端召回白名单、
 * S6/S7 闸门与「模型原始文本绝不直接进 JDBC」全部是代码硬约束，无法通过本配置关闭。
 * 这里只调「重试预算 / 注入检测开关 / 结果解读开关 / 长度上限」等量。
 */
@Component
@ConfigurationProperties(prefix = "data-query.chat")
@Data
public class ChatOrchestratorProperties {

    /** 用户问题长度上限（字符），超出直接拒绝 */
    private Integer maxQuestionLength = 500;

    /** 会话历史带入的轮数 */
    private Integer historyRounds = 5;

    /** A 方案：会话内追问的轻量规则改写是否启用（纯规则，零 LLM 调用；关闭后召回只看当前轮问题） */
    private Boolean followupRewriteEnabled = true;

    /** SQL_FIX + S6/S7 共享的模型修正总预算（次）；用完即停，防止成本放大 */
    private Integer maxFixRounds = 2;

    /** 意图 / 改写等前置模型调用的最大轮数；0 表示纯规则、不调用模型 */
    private Integer maxIntentRounds = 1;

    /** 提示词注入检测是否启用（安全建议常开） */
    private Boolean promptInjectionGuardEnabled = true;

    /** S8 结果解读（RESULT_SUMMARY）是否启用；失败 / 超时不阻断主结果 */
    private Boolean resultSummaryEnabled = false;

    /** S5 模型调用失败时是否允许走参数化模板降级（一期未落模板，保留开关） */
    private Boolean templateFallbackEnabled = false;

    /** 单次 ask 端到端硬超时（毫秒） */
    private Integer totalTimeoutMs = 60000;

    /** HTTP 调用超时（毫秒），传给 ModelInvocation */
    private Integer modelTimeoutMs = 30000;

    /** HTTP 连接超时（毫秒），用于构造 OpenAI 兼容客户端 */
    private Integer modelConnectTimeoutMs = 5000;

    /** resultSummary 场景的额外模型调用超时（毫秒） */
    private Integer summaryTimeoutMs = 10000;

    // ---------- P6：SSE 流式与 S8 结果加工 ----------

    /** 是否启用 /ask-stream；关闭后流式端点返回明确拒绝 */
    private Boolean sseEnabled = true;

    /**
     * SSE 并发流上限。
     *
     * <p>线程池按「固定大小」创建：core 恒等于 max，空闲线程 60s 后回收。
     * 不设独立的核心线程数——ThreadPoolExecutor 只在队列满时才把线程扩到 max，
     * 一旦核心数小于上限，实际并发就等于核心数，上限形同虚设（流式下每条约占 10~30s）。
     */
    private Integer sseMaxThreads = 32;

    /** SSE 任务队列容量；并发流打满后排队，队列也满则拒绝新请求（072），不占 Tomcat 线程等待 */
    private Integer sseQueueCapacity = 64;

    /**
     * SSE 心跳间隔（毫秒）；<=0 关闭心跳。
     *
     * <p>心跳用 SSE 注释帧（{@code :ping}），不进入前端事件解析。除防中间设备 idle 断连外，
     * 它还是唯一的写探针：长时间没有 delta 时，只有主动写才能发现客户端已经消失。
     */
    private Integer heartbeatIntervalMs = 15000;

    /** SseEmitter 超时（毫秒），应略大于 totalTimeoutMs */
    private Integer sseTimeoutMs = 70000;

    /** 是否生成规则图表推荐（不调用模型、不额外查询数据库） */
    private Boolean chartSuggestionEnabled = true;

    /** 是否生成结构化口径说明（不调用模型、不输出业务数据行） */
    private Boolean resultExplainEnabled = true;
}
