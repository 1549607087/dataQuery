package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelCallLog;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.framework.chat.CancelledException;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelToken;
import cn.iocoder.boot.module.data_query.service.AiModelCallLogService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型调用治理执行器 —— 把 M1 的配置真正串成运行时链路
 *
 * 单次调用顺序：路由 → QPS 限流 → 熔断判定 → 并发信号量（记录排队耗时）
 *   → 硬超时调用 → 可重试错误分类 + 指数退避抖动 → 跨供应商降级 → 全量日志埋点。
 *
 * 三个容易踩的坑，这里都做了约束：
 * 1) 重试只对配置白名单里的错误生效；401 / 400 / 内容审核拒绝绝不重试。
 * 2) 降级链必须跨供应商（fallback_cross_provider），否则供应商整体故障时整条链一起挂。
 * 3) 每次尝试都写一条调用日志，retry_of_trace_id 指向原始 traceId，
 *    成本按 root trace 聚合，避免「首次已成功但响应丢失」被记成多条独立成功。
 *
 * 超时的实现边界：治理层用 Future.get 保证调用方不再等待，并 cancel(true) 发出中断；
 * 但只有 ModelClient 实现响应中断 / 自身设置了 HTTP read timeout，底层连接才会真正释放。
 */
@Slf4j
@Component
public class ModelGovernanceExecutor {

    private static final int DEFAULT_TIMEOUT_MS = 8000;
    private static final int DEFAULT_RETRY_COUNT = 0;

    @Resource
    private ModelRouter modelRouter;
    @Resource
    private ModelRateLimiter modelRateLimiter;
    @Resource
    private ModelCircuitBreaker modelCircuitBreaker;
    @Resource
    private AiModelCallLogService aiModelCallLogService;

    /** 独立线程池：LLM 是慢 IO，不能占用 Web 容器线程 */
    private ExecutorService invokePool;

    @PostConstruct
    public void init() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ai-model-invoke-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        invokePool = new ThreadPoolExecutor(cores * 2, cores * 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    public void destroy() {
        if (invokePool != null) {
            invokePool.shutdownNow();
        }
    }

    /**
     * 执行一次受治理的模型调用
     *
     * @param invocation 调用上下文
     * @param client     真实 LLM 客户端；为 null 时直接报错，不会静默返回假数据
     */
    public ModelInvocationResult execute(ModelInvocation invocation, ModelClient client) {
        if (client == null) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "未接入 ModelClient 实现，无法发起模型调用");
        }
        prepare(invocation);
        String rootTrace = invocation.getTraceId();
        long requestStart = System.currentTimeMillis();

        ModelRoutePlan plan = modelRouter.route(invocation);
        AiModelSceneBinding binding = plan.getBinding();
        List<AiModelConfig> candidates = plan.getCandidates();
        if (plan.getPromptIdOverride() != null && plan.getPromptIdOverride() > 0) {
            invocation.setPromptId(plan.getPromptIdOverride());
        }

        Long previousModelConfigId = 0L;
        Long previousProviderId = null;
        ChatCancelToken cancelToken = invocation.getCancelToken();
        for (int depth = 0; depth < candidates.size(); depth++) {
            // 取消断路：进入任何候选前检查，已取消则不再发起新模型的 HTTP 调用
            checkCancelled(cancelToken);
            AiModelConfig config = candidates.get(depth);
            String circuitKey = ModelCircuitBreaker.circuitKey(config);

            // 跨供应商降级校验：与上一个失败的候选同供应商则跳过
            if (depth > 0 && binding.getFallbackCrossProvider() != null
                    && binding.getFallbackCrossProvider() == 1
                    && Objects.equals(config.getProviderId(), previousProviderId)) {
                log.info("[execute][跳过同供应商候选 modelId={} providerId={}]", config.getId(), config.getProviderId());
                continue;
            }

            String circuitState = modelCircuitBreaker.currentState(circuitKey);
            if (!modelCircuitBreaker.allowRequest(config)) {
                writeLog(invocation, plan, config, depth, 0, 0L, circuitState,
                        ModelErrorCategory.CIRCUIT_OPEN, 1, 0, 0, 0, null, null, previousModelConfigId);
                previousProviderId = config.getProviderId();
                previousModelConfigId = config.getId();
                continue;
            }

            int timeoutMs = resolve(binding.getTimeoutMsOverride(), config.getTimeoutMs(), DEFAULT_TIMEOUT_MS);
            int qpsLimit = resolve(binding.getQpsLimitOverride(), config.getQpsLimit(), 0);
            int concurrentLimit = resolve(binding.getConcurrentLimitOverride(), config.getConcurrentLimit(), 0);
            int retryCount = resolve(binding.getRetryCountOverride(), config.getRetryCount(), DEFAULT_RETRY_COUNT);
            retryCount = Math.max(0, Math.min(retryCount, 5)); // 硬上限，防止配置写错把成本放大
            invocation.setTimeoutMs(timeoutMs);

            for (int attempt = 0; attempt <= retryCount; attempt++) {
                // 取消断路：每次重试前检查（含首轮），已取消立即抛出，不再占用信号量/发起 HTTP
                checkCancelled(cancelToken);
                if (!modelRateLimiter.tryAcquireQps(circuitKey, qpsLimit)) {
                    // 本地限流不是下游故障：不重试、不熔断，直接换候选
                    writeLog(invocation, plan, config, depth, attempt, 0L, circuitState,
                            ModelErrorCategory.LOCAL_RATE_LIMIT, 1, 0, 0, 0, null, null, previousModelConfigId);
                    break;
                }
                long waitStart = System.currentTimeMillis();
                boolean acquired = modelRateLimiter.tryAcquireConcurrency(circuitKey, concurrentLimit, timeoutMs);
                long queueWaitMs = System.currentTimeMillis() - waitStart;
                if (!acquired) {
                    writeLog(invocation, plan, config, depth, attempt, queueWaitMs, circuitState,
                            ModelErrorCategory.QUEUE_FULL, 1, 0, 0, 0, null, null, previousModelConfigId);
                    break;
                }

                long callStart = System.currentTimeMillis();
                Future<ModelInvocationResult> future = null;
                try {
                    future = invokePool.submit(() -> client.invoke(invocation, config));
                    ModelInvocationResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    if (cancelToken != null && cancelToken.isCancelled()) {
                        if (future != null) {
                            future.cancel(true);
                        }
                        throw new CancelledException(cancelToken.getClientRequestId());
                    }
                    modelCircuitBreaker.recordSuccess(config);
                    int latency = (int) (System.currentTimeMillis() - callStart);
                    fillResult(result, invocation, plan, config, depth, rootTrace, latency);
                    BigDecimal cost = calcCost(config, result);
                    writeLog(invocation, plan, config, depth, attempt, queueWaitMs, circuitState,
                            null, depth > 0 ? 3 : 0, latency,
                            nvl(result.getRequestTokens()), nvl(result.getResponseTokens()),
                            result.getRequestId(), cost, previousModelConfigId);
                    return result;
                } catch (Exception ex) {
                    if (future != null) {
                        future.cancel(true);
                    }
                    // 取消短路：future 被 cancel 会抛 CancellationException/InterruptedException，
                    // 此时绝不能按 TIMEOUT 分类继续重试——否则「停止一次」反而发起更多 HTTP 请求。
                    if (cancelToken != null && cancelToken.isCancelled()) {
                        throw new CancelledException(cancelToken.getClientRequestId());
                    }
                    Throwable cause = unwrap(ex);
                    ModelErrorCategory category = classify(cause, ex);
                    int latency = (int) (System.currentTimeMillis() - callStart);
                    modelCircuitBreaker.recordFailure(config, category, category.name());
                    writeLog(invocation, plan, config, depth, attempt, queueWaitMs, circuitState,
                            category, category == ModelErrorCategory.TIMEOUT ? 2 : 1,
                            latency, 0, 0, null, BigDecimal.ZERO, previousModelConfigId);
                    if (!category.retryable(config.getRetryableErrors())) {
                        break;
                    }
                    if (attempt < retryCount && !backoff(config, attempt)) {
                        break;
                    }
                } finally {
                    modelRateLimiter.releaseConcurrency(circuitKey, concurrentLimit);
                }
            }
            previousProviderId = config.getProviderId();
            previousModelConfigId = config.getId();
        }
        throw new ModelException(ModelErrorCategory.ALL_FAILED,
                "场景 " + invocation.getSceneCode() + " 的重试与降级链已全部耗尽，traceId=" + rootTrace
                        + "，耗时 " + (System.currentTimeMillis() - requestStart) + "ms");
    }

    /**
     * 执行一次受治理的流式模型调用。
     *
     * <p>与 {@link #execute} 的唯一实质差异是<b>提交点</b>：<b>首个 delta 交给下游即为提交点</b>。
     * <ul>
     *   <li>提交前：客户端还没看到任何文本，重试 / 换模型对用户无感，与同步链路口径完全一致；</li>
     *   <li>提交后：客户端已看到部分文本，只能收口 —— 不重试、不降级。换模型会拼出
     *       「前半段 A 模型、后半段 B 模型」的混合文本，比直接中断更糟。</li>
     * </ul>
     *
     * <p>线程模型：流式调用<b>不提交到 invokePool</b>，直接在调用方线程（SSE 连接自身的线程）阻塞执行。
     * 原因有二：一是流式调用天然是长阻塞（数十秒），占 invokePool 会挤掉同步链路的调用槽；
     * 二是取消靠 ChatCancelToken 的 dispose 而不是 Future.cancel，不需要线程池 Future。
     *
     * <p>代价：并发信号量的持有时间从「一次调用 ~2s」变成「整个回答 ~10-30s」，
     * 若 RAG 场景配置了 concurrent_limit，需按活跃流数量重新评估。
     */
    public ModelInvocationResult executeStream(ModelInvocation invocation, StreamModelClient client, DeltaSink sink) {
        if (client == null) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "未接入 StreamModelClient 实现，无法发起流式模型调用");
        }
        if (sink == null) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR, "流式调用缺少增量输出口");
        }
        prepare(invocation);
        String rootTrace = invocation.getTraceId();
        long requestStart = System.currentTimeMillis();

        ModelRoutePlan plan = modelRouter.route(invocation);
        AiModelSceneBinding binding = plan.getBinding();
        List<AiModelConfig> candidates = plan.getCandidates();
        if (plan.getPromptIdOverride() != null && plan.getPromptIdOverride() > 0) {
            invocation.setPromptId(plan.getPromptIdOverride());
        }

        Long previousModelConfigId = 0L;
        Long previousProviderId = null;
        ChatCancelToken cancelToken = invocation.getCancelToken();
        for (int depth = 0; depth < candidates.size(); depth++) {
            checkCancelled(cancelToken);
            AiModelConfig config = candidates.get(depth);
            String circuitKey = ModelCircuitBreaker.circuitKey(config);

            if (depth > 0 && binding.getFallbackCrossProvider() != null
                    && binding.getFallbackCrossProvider() == 1
                    && Objects.equals(config.getProviderId(), previousProviderId)) {
                log.info("[executeStream][跳过同供应商候选 modelId={} providerId={}]",
                        config.getId(), config.getProviderId());
                continue;
            }

            String circuitState = modelCircuitBreaker.currentState(circuitKey);
            if (!modelCircuitBreaker.allowRequest(config)) {
                writeLog(invocation, plan, config, depth, 0, 0L, circuitState,
                        ModelErrorCategory.CIRCUIT_OPEN, 1, 0, 0, 0, null, null, previousModelConfigId);
                previousProviderId = config.getProviderId();
                previousModelConfigId = config.getId();
                continue;
            }

            int timeoutMs = resolve(binding.getTimeoutMsOverride(), config.getTimeoutMs(), DEFAULT_TIMEOUT_MS);
            int qpsLimit = resolve(binding.getQpsLimitOverride(), config.getQpsLimit(), 0);
            int concurrentLimit = resolve(binding.getConcurrentLimitOverride(), config.getConcurrentLimit(), 0);
            int retryCount = resolve(binding.getRetryCountOverride(), config.getRetryCount(), DEFAULT_RETRY_COUNT);
            retryCount = Math.max(0, Math.min(retryCount, 5));
            invocation.setTimeoutMs(timeoutMs);

            for (int attempt = 0; attempt <= retryCount; attempt++) {
                checkCancelled(cancelToken);
                if (!modelRateLimiter.tryAcquireQps(circuitKey, qpsLimit)) {
                    writeLog(invocation, plan, config, depth, attempt, 0L, circuitState,
                            ModelErrorCategory.LOCAL_RATE_LIMIT, 1, 0, 0, 0, null, null, previousModelConfigId);
                    break;
                }
                long waitStart = System.currentTimeMillis();
                boolean acquired = modelRateLimiter.tryAcquireConcurrency(circuitKey, concurrentLimit, timeoutMs);
                long queueWaitMs = System.currentTimeMillis() - waitStart;
                if (!acquired) {
                    writeLog(invocation, plan, config, depth, attempt, queueWaitMs, circuitState,
                            ModelErrorCategory.QUEUE_FULL, 1, 0, 0, 0, null, null, previousModelConfigId);
                    break;
                }

                // 提交点标记：AtomicBoolean 而非 boolean，客户端可能在 Reactor 线程回调 onDelta
                AtomicBoolean committed = new AtomicBoolean(false);
                long callStart = System.currentTimeMillis();
                try {
                    ModelInvocationResult result = client.stream(invocation, config, delta -> {
                        committed.set(true);
                        sink.onDelta(delta);
                    });
                    if (cancelToken != null && cancelToken.isCancelled()) {
                        throw new CancelledException(cancelToken.getClientRequestId());
                    }
                    modelCircuitBreaker.recordSuccess(config);
                    int latency = (int) (System.currentTimeMillis() - callStart);
                    fillResult(result, invocation, plan, config, depth, rootTrace, latency);
                    BigDecimal cost = calcCost(config, result);
                    writeLog(invocation, plan, config, depth, attempt, queueWaitMs, circuitState,
                            null, depth > 0 ? 3 : 0, latency,
                            nvl(result.getRequestTokens()), nvl(result.getResponseTokens()),
                            result.getRequestId(), cost, previousModelConfigId);
                    return result;
                } catch (Exception ex) {
                    // 取消短路必须在分类之前：dispose 引起的异常不能算成下游故障
                    if (cancelToken != null && cancelToken.isCancelled()) {
                        throw new CancelledException(cancelToken.getClientRequestId());
                    }
                    Throwable cause = unwrap(ex);
                    ModelErrorCategory category = classify(cause, ex);
                    int latency = (int) (System.currentTimeMillis() - callStart);
                    modelCircuitBreaker.recordFailure(config, category, category.name());
                    writeLog(invocation, plan, config, depth, attempt, queueWaitMs, circuitState,
                            category, category == ModelErrorCategory.TIMEOUT ? 2 : 1,
                            latency, 0, 0, null, BigDecimal.ZERO, previousModelConfigId);

                    if (committed.get()) {
                        log.warn("[executeStream][已提交后中断，不再重试/降级] traceId={} modelId={} category={} cause={}",
                                rootTrace, config.getId(), category, abbreviate(safeMessage(ex), 200));
                        if (ex instanceof ModelException modelException) {
                            throw modelException;
                        }
                        throw new ModelException(category,
                                "流式回答已推送部分内容后中断：" + safeMessage(ex), ex);
                    }
                    if (!category.retryable(config.getRetryableErrors())) {
                        break;
                    }
                    if (attempt < retryCount && !backoff(config, attempt)) {
                        break;
                    }
                } finally {
                    modelRateLimiter.releaseConcurrency(circuitKey, concurrentLimit);
                }
            }
            previousProviderId = config.getProviderId();
            previousModelConfigId = config.getId();
        }
        throw new ModelException(ModelErrorCategory.ALL_FAILED,
                "场景 " + invocation.getSceneCode() + " 的流式重试与降级链已全部耗尽，traceId=" + rootTrace
                        + "，耗时 " + (System.currentTimeMillis() - requestStart) + "ms");
    }

    // ========== 内部方法 ==========

    /**
     * 固化租户号与 traceId：日志 / 灰度分桶 / 熔断键都必须以 invocation 的固化值为准，
     * 不能依赖线程上的 ThreadLocal（异步线程池里会丢）。
     */
    private void prepare(ModelInvocation invocation) {
        if (invocation.getTenantId() == null) {
            Long currentTenantId = TenantContextHolder.getTenantId();
            invocation.setTenantId(currentTenantId == null ? 1L : currentTenantId);
        }
        if (invocation.getTraceId() == null || invocation.getTraceId().isBlank()) {
            invocation.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        }
    }

    /** 异常消息脱敏 + 截断：凭证/连接串不回显 */
    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            return ex.getClass().getSimpleName();
        }
        String lower = message.toLowerCase();
        if (lower.contains("api key") || lower.contains("authorization") || lower.contains("password")) {
            return "调用异常（凭证不回显）";
        }
        return message;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 取消检查：令牌为空时零开销；已取消则抛出，交由编排器以 STOPPED 收口。 */
    private static void checkCancelled(ChatCancelToken cancelToken) {
        if (cancelToken != null && cancelToken.isCancelled()) {
            throw new CancelledException(cancelToken.getClientRequestId());
        }
    }

    private void fillResult(ModelInvocationResult result, ModelInvocation invocation,
                            ModelRoutePlan plan, AiModelConfig config, int depth,
                            String rootTrace, int latency) {
        result.setTraceId(rootTrace);
        result.setRouteStrategy(plan.getRouteStrategy());
        result.setFallbackDepth(depth);
        result.setIsGray(Boolean.TRUE.equals(plan.getGray()) ? 1 : 0);
        result.setGrayGroup(plan.getGrayGroup());
        result.setLatencyMs(latency);
        result.setModelId(config.getModelId());
        result.setModelConfigId(config.getId());
    }

    private BigDecimal calcCost(AiModelConfig config, ModelInvocationResult result) {
        BigDecimal inPrice = config.getInputPrice() == null ? BigDecimal.ZERO : config.getInputPrice();
        BigDecimal outPrice = config.getOutputPrice() == null ? BigDecimal.ZERO : config.getOutputPrice();
        BigDecimal inCost = inPrice.multiply(BigDecimal.valueOf(nvl(result.getRequestTokens())));
        BigDecimal outCost = outPrice.multiply(BigDecimal.valueOf(nvl(result.getResponseTokens())));
        return inCost.add(outCost)
                .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
    }

    /** 每次尝试写一条日志；retry_of_trace_id 让成本可以按原始 trace 聚合 */
    private void writeLog(ModelInvocation invocation, ModelRoutePlan plan, AiModelConfig config,
                          int fallbackDepth, int retryIndex, long queueWaitMs, String circuitState,
                          ModelErrorCategory category, int status, int latencyMs,
                          int requestTokens, int responseTokens, String requestId,
                          BigDecimal cost, Long fallbackFromModelId) {
        try {
            AiModelCallLog entity = new AiModelCallLog();
            entity.setTraceId(invocation.getTraceId());
            entity.setConversationId(invocation.getConversationId());
            entity.setSceneCode(invocation.getSceneCode());
            entity.setProviderId(config.getProviderId());
            entity.setModelConfigId(config.getId());
            entity.setModelId(config.getModelId());
            entity.setRequestTokens(requestTokens);
            entity.setResponseTokens(responseTokens);
            entity.setTotalTokens(requestTokens + responseTokens);
            entity.setCost(cost == null ? BigDecimal.ZERO : cost);
            entity.setLatencyMs(latencyMs);
            entity.setStatus(status);
            entity.setFallbackFromModelId(fallbackFromModelId == null ? 0L : fallbackFromModelId);
            entity.setErrorCode(category == null ? "" : category.name());
            entity.setErrorMsg(category == null ? "" : category.name());
            entity.setUserId(invocation.getUserId());
            entity.setIsGray(Boolean.TRUE.equals(plan.getGray()) ? 1 : 0);
            entity.setGrayGroup(plan.getGrayGroup());
            entity.setGrayExperimentId(plan.getGrayExperimentId());
            entity.setGrayBucketKey(plan.getGrayBucketKey());
            entity.setRetryOfTraceId(fallbackDepth == 0 && retryIndex == 0 ? "" : invocation.getTraceId());
            entity.setRetryIndex(retryIndex);
            entity.setCircuitState(circuitState);
            entity.setCircuitKey(ModelCircuitBreaker.circuitKey(config));
            entity.setErrorCategory(category == null ? "" : category.name());
            entity.setFallbackDepth(fallbackDepth);
            entity.setRouteStrategy(plan.getRouteStrategy());
            entity.setQueueWaitMs((int) queueWaitMs);
            entity.setRequestId(requestId == null ? "" : requestId);
            LocalDateTime now = LocalDateTime.now();
            String user = currentUser();
            entity.setCreator(user);
            entity.setUpdater(user);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDeleted(0);
            // 私有表不放行，但实体是普通 POJO、INSERT 不会被 DefaultDBFieldHandler 自动填充 tenant_id，
            // 必须显式写入，否则日志会落到默认 0（平台）而查不出来，也影响计费归属
            entity.setTenantId(invocation.getTenantId());
            aiModelCallLogService.save(entity);
        } catch (Exception ex) {
            // 日志写入失败不能影响主链路
            log.warn("[writeLog][调用日志写入失败 traceId={}] {}", invocation.getTraceId(), ex.getMessage());
        }
    }

    private Throwable unwrap(Exception ex) {
        if (ex instanceof ExecutionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }

    private ModelErrorCategory classify(Throwable cause, Exception original) {
        if (cause instanceof TimeoutException || original instanceof TimeoutException) {
            return ModelErrorCategory.TIMEOUT;
        }
        if (cause instanceof RejectedExecutionException || original instanceof RejectedExecutionException) {
            return ModelErrorCategory.QUEUE_FULL;
        }
        return ModelErrorCategory.from(cause);
    }

    /** 指数退避 + 抖动，避免多实例在同一刻重试形成同步风暴 */
    private boolean backoff(AiModelConfig config, int attempt) {
        long base = config.getRetryBaseBackoffMs() == null || config.getRetryBaseBackoffMs() <= 0
                ? 200 : config.getRetryBaseBackoffMs();
        long max = config.getRetryMaxBackoffMs() == null || config.getRetryMaxBackoffMs() <= 0
                ? 2000 : config.getRetryMaxBackoffMs();
        double jitter = config.getRetryJitterRatio() == null
                ? 0.30 : config.getRetryJitterRatio().doubleValue();
        long delay = Math.min(max, base * (1L << Math.min(attempt, 10)));
        long range = (long) (delay * Math.max(0, Math.min(jitter, 1.0)));
        long actual = range == 0 ? delay : delay - range / 2 + (long) (Math.random() * range);
        try {
            Thread.sleep(Math.max(1, actual));
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private int resolve(Integer override, Integer modelValue, int defaultValue) {
        if (override != null) {
            return override;
        }
        return modelValue == null ? defaultValue : modelValue;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

}
