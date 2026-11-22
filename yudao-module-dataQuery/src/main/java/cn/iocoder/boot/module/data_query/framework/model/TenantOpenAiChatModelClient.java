package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelProvider;
import cn.iocoder.boot.module.data_query.framework.chat.CancelledException;
import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelToken;
import cn.iocoder.boot.module.data_query.framework.chat.ChatOrchestratorProperties;
import cn.iocoder.boot.module.data_query.service.AiModelProviderService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * P5 真实 Chat 模型客户端（OpenAI 兼容协议）。
 *
 * 与 P2 的 TenantSchemaEmbeddingProvider 同一口径：
 * 1. 凭证【每租户独立】：治理层路由选中的 AiModelConfig 只带 providerId，
 *    这里按 invocation.tenantId 显式查本租户的 ai_model_provider（api_key 由
 *    AiSecretTypeHandler 解密），绝不读 spring.ai.openai.api-key 全局 Bean，
 *    也不信任 ThreadLocal 的租户上下文（治理层在异步线程池中调用）。
 * 2. 只发一次 OpenAI 兼容 /v1/chat/completions 请求，不做治理：
 *    路由 / 限流 / 熔断 / 重试 / 降级 / 日志全部由 ModelGovernanceExecutor 负责。
 *    这里唯一要做的就是「按准确分类抛 ModelException」，让治理层的重试与熔断决策有依据。
 * 3. 失败绝不伪造结果：任何环节失败都抛 ModelException，content 只来自供应商真实响应。
 * 4. 客户端按 (tenantId, 凭证指纹, 超时) 缓存；换 Key / 换供应商 / 换模型立即失效。
 *
 * 为什么不用 Spring AI 的 OpenAiChatModel / OpenAiApi#chatCompletionEntity：
 * 该 API 用 RestClient 直接把响应反序列化成 ChatCompletion 记录，对
 * Content-Type 与消息转换器要求较严（实测兼容网关返回
 * `application/json; charset=utf-8` 时会被判成 octet-stream 而抽取失败）。
 * 这里直接用 RestClient 取字符串再用 Jackson 解析，避开转换器耦合，
 * 同时能精确读取 usage / id 做成本对账。Embedding 侧仍复用 Spring AI（已实测可用）。
 *
 * 设 data-query.chat.enabled=false 可回退到 EchoModelClient（联调 / 运维降级开关）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data-query.chat", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TenantOpenAiChatModelClient implements ModelClient, StreamModelClient {

    private static final String PROTOCOL_OPENAI = "OPENAI";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_STREAM_FIRST_TOKEN_MS = 15000;
    private static final int DEFAULT_STREAM_IDLE_MS = 15000;
    /** 流式兜底等待上限：正常路径由 Reactor 的两段式超时与取消令牌终结，这里只防「全都失灵的极端泄漏」 */
    private static final long STREAM_MAX_WAIT_MS = 30 * 60 * 1000L;
    /** 与 OpenAI 兼容供应商约定一致的补全路径 */
    private static final String COMPLETIONS_PATH = "/v1/chat/completions";

    @Resource
    private AiModelProviderService modelProviderService;
    @Resource
    private ChatOrchestratorProperties properties;

    /** key = tenantId|fingerprint|timeoutMs；换凭证 / 换模型 / 换超时自动失效 */
    private final ConcurrentHashMap<String, RestClient> clients = new ConcurrentHashMap<>();
    /** 可取消路径专用：同样 key 的 WebClient 缓存（仅流式 ask 携带取消令牌时按需构造） */
    private final ConcurrentHashMap<String, WebClient> webClients = new ConcurrentHashMap<>();
    /** 每个租户当前缓存键，换配置时清理旧客户端 */
    private final ConcurrentHashMap<Long, String> clientKeyByTenant = new ConcurrentHashMap<>();

    @Override
    public ModelInvocationResult invoke(ModelInvocation invocation, AiModelConfig config) throws Exception {
        Long tenantId = invocation.getTenantId();
        if (tenantId == null) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "Chat 调用缺少 tenantId，无法解析本租户凭证");
        }
        // ModelGovernanceExecutor 用普通线程池异步调用，TenantContextHolder 不会跨线程传播；
        // 下面查 ai_model_provider 会触发租户拦截器，必须在这里显式设置并在 finally 清理。
        Long previousTenantId = TenantContextHolder.getTenantId();
        if (!tenantId.equals(previousTenantId)) {
            TenantContextHolder.setTenantId(tenantId);
        }
        try {
            AiModelProvider provider = resolveProvider(tenantId, config);
            int connectTimeoutMs = property(properties.getModelConnectTimeoutMs(), DEFAULT_CONNECT_TIMEOUT_MS);
            int readTimeoutMs = property(invocation.getTimeoutMs(), property(properties.getModelTimeoutMs(), 30000));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getModelId());
            body.put("messages", List.of(Map.of("role", "user",
                    "content", invocation.getPrompt() == null ? "" : invocation.getPrompt())));
            body.put("temperature", defaultTemperature(config));
            body.put("stream", false);

            try {
                ChatCancelToken cancelToken = invocation.getCancelToken();
                String raw;
                if (cancelToken != null) {
                    // 可取消路径（流式 ask）：WebClient + doOnSubscribe 把订阅注册到令牌，
                    // 取消时 dispose 订阅会断开底层 socket，使 block() 立即抛异常。
                    // 仍是 stream=false，一次拿完整响应，不改变语义。
                    raw = invokeCancellable(tenantId, provider, config, body,
                            connectTimeoutMs, readTimeoutMs, cancelToken);
                } else {
                    // 无令牌路径（同步 ask / 重新生成 / 表格叙述等）：保持原 RestClient 实现，零行为变化
                    RestClient client = getOrCreateClient(tenantId, provider, config, connectTimeoutMs, readTimeoutMs);
                    raw = client.post()
                            .uri(COMPLETIONS_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);
                }
                return parse(raw, config);
            } catch (Exception ex) {
                ModelErrorCategory category = classify(ex);
                log.warn("[chat][invoke-failed] tenantId={} baseUrl={} model={} category={} cause={}",
                        tenantId, provider.getBaseUrl(), config.getModelId(), category, ex.toString());
                throw new ModelException(category, "Chat 模型调用失败 tenantId=" + tenantId
                        + " model=" + config.getModelId() + ": " + ex.getMessage(), ex);
            }
        } finally {
            if (previousTenantId == null) {
                TenantContextHolder.clear();
            } else if (!previousTenantId.equals(tenantId)) {
                TenantContextHolder.setTenantId(previousTenantId);
            }
        }
    }

    // ==================== 流式调用 ====================

    /**
     * 流式调用（OpenAI 兼容 {@code stream=true}）。
     *
     * <p>与 {@link #invoke} 共用凭证解析、客户端缓存、错误分类，只有三处差异：
     * <ol>
     *   <li>请求体带 {@code stream=true} 与 {@code stream_options.include_usage=true}；
     *       后者部分兼容网关不认并直接 400，此时去掉参数重试一次（请求被拒时必然还没产生增量，重试安全）；</li>
     *   <li>超时改两段式：首 token 上限（未提交，治理层可重试/降级）+ 相邻增量间隔上限（已提交，只能终止）；</li>
     *   <li>取消靠 dispose 订阅断 socket，而不是 Future.cancel。</li>
     * </ol>
     */
    @Override
    public ModelInvocationResult stream(ModelInvocation invocation, AiModelConfig config, DeltaSink sink) throws Exception {
        Long tenantId = invocation.getTenantId();
        if (tenantId == null) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "Chat 流式调用缺少 tenantId，无法解析本租户凭证");
        }
        Long previousTenantId = TenantContextHolder.getTenantId();
        if (!tenantId.equals(previousTenantId)) {
            TenantContextHolder.setTenantId(tenantId);
        }
        try {
            AiModelProvider provider = resolveProvider(tenantId, config);
            int connectTimeoutMs = property(properties.getModelConnectTimeoutMs(), DEFAULT_CONNECT_TIMEOUT_MS);
            int firstTokenMs = property(invocation.getStreamFirstTokenMs(), DEFAULT_STREAM_FIRST_TOKEN_MS);
            int idleMs = property(invocation.getStreamIdleMs(), DEFAULT_STREAM_IDLE_MS);
            try {
                return streamWithUsageFallback(tenantId, provider, config, invocation, sink,
                        connectTimeoutMs, firstTokenMs, idleMs);
            } catch (CancelledException | ModelException ex) {
                throw ex;
            } catch (Exception ex) {
                ModelErrorCategory category = classify(ex);
                log.warn("[chat][stream-failed] tenantId={} baseUrl={} model={} category={} cause={}",
                        tenantId, provider.getBaseUrl(), config.getModelId(), category, ex.toString());
                throw new ModelException(category, "Chat 流式调用失败 tenantId=" + tenantId
                        + " model=" + config.getModelId() + ": " + ex.getMessage(), ex);
            }
        } finally {
            if (previousTenantId == null) {
                TenantContextHolder.clear();
            } else if (!previousTenantId.equals(tenantId)) {
                TenantContextHolder.setTenantId(previousTenantId);
            }
        }
    }

    /**
     * {@code stream_options.include_usage} 兼容降级：只有「本次尝试一个增量都没发出去」才重试，
     * 否则会把前半段文本重复推给用户。
     */
    private ModelInvocationResult streamWithUsageFallback(Long tenantId, AiModelProvider provider,
                                                          AiModelConfig config, ModelInvocation invocation,
                                                          DeltaSink sink, int connectTimeoutMs,
                                                          int firstTokenMs, int idleMs) throws Exception {
        AtomicInteger emittedChars = new AtomicInteger();
        DeltaSink countingSink = delta -> {
            emittedChars.addAndGet(delta == null ? 0 : delta.length());
            sink.onDelta(delta);
        };
        try {
            return doStream(tenantId, provider, config, invocation, countingSink,
                    true, connectTimeoutMs, firstTokenMs, idleMs);
        } catch (Exception ex) {
            ModelErrorCategory category = ex instanceof ModelException me
                    ? me.getCategory() : ModelErrorCategory.from(ex);
            if (category == ModelErrorCategory.BAD_REQUEST && emittedChars.get() == 0) {
                log.warn("[chat][stream] 网关疑似不支持 stream_options.include_usage（400），去掉该参数重试 model={}",
                        config.getModelId());
                return doStream(tenantId, provider, config, invocation, countingSink,
                        false, connectTimeoutMs, firstTokenMs, idleMs);
            }
            throw ex;
        }
    }

    private ModelInvocationResult doStream(Long tenantId, AiModelProvider provider, AiModelConfig config,
                                           ModelInvocation invocation, DeltaSink sink, boolean includeUsage,
                                           int connectTimeoutMs, int firstTokenMs, int idleMs) throws Exception {
        WebClient client = getOrCreateWebClient(tenantId, provider, config, connectTimeoutMs, idleMs);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelId());
        body.put("messages", List.of(Map.of("role", "user",
                "content", invocation.getPrompt() == null ? "" : invocation.getPrompt())));
        body.put("temperature", defaultTemperature(config));
        body.put("stream", true);
        if (includeUsage) {
            body.put("stream_options", Map.of("include_usage", true));
        }

        StringBuilder content = new StringBuilder();
        AtomicReference<String> requestId = new AtomicReference<>("");
        AtomicReference<String> modelName = new AtomicReference<>(config.getModelId());
        AtomicInteger promptTokens = new AtomicInteger(0);
        AtomicInteger completionTokens = new AtomicInteger(0);
        AtomicInteger totalTokens = new AtomicInteger(0);
        AtomicBoolean usageSeen = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        Flux<String> flux = client.post()
                .uri(COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                // 两段式超时：首 token 与增量间隔口径不同（前者可重试、后者只能终止），
                // 不能用 model-timeout-ms 一刀切——那会把长回答说到一半硬砍
                .timeout(Mono.delay(Duration.ofMillis(firstTokenMs)),
                        frame -> Mono.delay(Duration.ofMillis(idleMs)).then())
                // 回调里要写 Servlet 响应（SseEmitter.send），这是有阻塞风险的操作，
                // 不能跑在 Reactor Netty 的 IO 线程上——一个慢客户端会拖住整条 event loop
                .publishOn(Schedulers.boundedElastic());

        Disposable subscription = flux
                .doFinally(signal -> finished.countDown())
                .subscribe(frame -> onFrame(frame, sink, content, requestId, modelName,
                                promptTokens, completionTokens, totalTokens, usageSeen),
                        failure::set);

        ChatCancelToken cancelToken = invocation.getCancelToken();
        if (cancelToken != null) {
            // 取消 → dispose → 断开底层 socket，供应商侧停止生成（这是省 token 的关键一步）
            cancelToken.register(subscription);
        }
        try {
            if (!finished.await(STREAM_MAX_WAIT_MS, TimeUnit.MILLISECONDS)) {
                subscription.dispose();
                throw new ModelException(ModelErrorCategory.TIMEOUT, "Chat 流式响应长时间未结束");
            }
        } catch (InterruptedException ex) {
            subscription.dispose();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat 流式调用被中断", ex);
        } finally {
            if (cancelToken != null) {
                cancelToken.unregister(subscription);
            }
        }
        // 取消时 Reactor 走 cancel 信号（doFinally 已放行 await），不会给 onError，必须在这里显式判定
        if (cancelToken != null && cancelToken.isCancelled()) {
            subscription.dispose();
            throw new CancelledException(cancelToken.getClientRequestId());
        }
        Throwable error = failure.get();
        if (error != null) {
            if (error instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(error.getMessage(), error);
        }
        if (content.length() == 0) {
            throw new IllegalStateException("Chat 流式响应 content 为空");
        }

        ModelInvocationResult result = new ModelInvocationResult();
        result.setContent(content.toString());
        result.setEcho(Boolean.FALSE);
        result.setRequestId(requestId.get());
        result.setModelId(StringUtils.hasText(modelName.get()) ? modelName.get() : config.getModelId());
        if (usageSeen.get()) {
            result.setRequestTokens(promptTokens.get());
            result.setResponseTokens(completionTokens.get());
            result.setTotalTokens(totalTokens.get());
        } else {
            // 网关未返回 usage：按字符数估算，只保证成本统计口径连续，不用于计费对账
            int request = estimateTokens(invocation.getPrompt());
            int response = estimateTokens(content.toString());
            result.setRequestTokens(request);
            result.setResponseTokens(response);
            result.setTotalTokens(request + response);
        }
        result.setModelConfigId(config.getId());
        return result;
    }

    /** 单帧处理：解析 → 更新元数据 → 立刻下发增量（不缓存整段） */
    private static void onFrame(String frame, DeltaSink sink, StringBuilder content,
                                AtomicReference<String> requestId, AtomicReference<String> modelName,
                                AtomicInteger promptTokens, AtomicInteger completionTokens,
                                AtomicInteger totalTokens, AtomicBoolean usageSeen) {
        StreamChunkParser.Chunk chunk = StreamChunkParser.parse(frame);
        if (chunk == null || chunk.isDone()) {
            return;
        }
        if (StringUtils.hasText(chunk.getRequestId())) {
            requestId.set(chunk.getRequestId());
        }
        if (StringUtils.hasText(chunk.getModel())) {
            modelName.set(chunk.getModel());
        }
        if (chunk.hasUsage()) {
            promptTokens.set(nvl(chunk.getPromptTokens()));
            completionTokens.set(nvl(chunk.getCompletionTokens()));
            totalTokens.set(nvl(chunk.getTotalTokens()));
            usageSeen.set(true);
        }
        String delta = chunk.getContent();
        if (!StringUtils.hasText(delta)) {
            return;
        }
        content.append(delta);
        try {
            sink.onDelta(delta);
        } catch (RuntimeException ex) {
            // 下游已不需要（断流 / 截断）：往上抛，Reactor 会 cancel 订阅断开连接
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("下游拒绝接收增量", ex);
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    /** 粗略 token 估算：CJK 按 1 字 ≈ 1 token，其余按 4 字符 ≈ 1 token */
    private static int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x2E80) {
                cjk++;
            }
        }
        int other = text.length() - cjk;
        return cjk + Math.max(1, other / 4);
    }

    // ==================== 响应解析 ====================

    private ModelInvocationResult parse(String raw, AiModelConfig config) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("Chat 响应正文为空");
        }
        JsonNode root;
        try {
            root = JSON.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Chat 响应不是合法 JSON：" + abbreviate(raw, 200));
        }
        // 部分网关把错误放在 200 响应体里
        if (root.has("error") && !root.get("error").isNull()) {
            String msg = root.path("error").path("message").asText(root.path("error").toString());
            throw new IllegalStateException("供应商返回错误：" + abbreviate(msg, 200));
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("Chat 响应无 choices");
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText("");
        if (!StringUtils.hasText(content)) {
            // 兼容部分网关返回 reasoning_content 而 content 为空
            content = message.path("reasoning_content").asText("");
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Chat 响应 content 为空");
        }

        ModelInvocationResult result = new ModelInvocationResult();
        result.setContent(content);
        result.setEcho(Boolean.FALSE);
        result.setRequestId(root.path("id").asText(""));
        String model = root.path("model").asText("");
        result.setModelId(StringUtils.hasText(model) ? model : config.getModelId());
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode() && !usage.isNull()) {
            result.setRequestTokens(usage.path("prompt_tokens").asInt(0));
            result.setResponseTokens(usage.path("completion_tokens").asInt(0));
            result.setTotalTokens(usage.path("total_tokens").asInt(0));
        } else {
            result.setRequestTokens(0);
            result.setResponseTokens(0);
            result.setTotalTokens(0);
        }
        result.setModelConfigId(config.getId());
        return result;
    }

    // ==================== 凭证解析 ====================

    /** 显式带 tenant_id：即使拦截器被误放行也拿不到别的租户凭证 */
    private AiModelProvider resolveProvider(Long tenantId, AiModelConfig config) {
        if (config.getProviderId() == null) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "Chat 模型配置未关联供应商，无法解析凭证");
        }
        List<AiModelProvider> list = modelProviderService.list(
                new LambdaQueryWrapperX<AiModelProvider>()
                        .eq(AiModelProvider::getId, config.getProviderId())
                        .eq(AiModelProvider::getTenantId, tenantId)
                        .eq(AiModelProvider::getDeleted, 0)
                        .last("LIMIT 1"));
        if (list.isEmpty()) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "tenantId=" + tenantId + " 未找到 providerId=" + config.getProviderId() + " 的供应商配置");
        }
        AiModelProvider provider = list.get(0);
        String protocol = StringUtils.hasText(provider.getProtocol())
                ? provider.getProtocol().trim().toUpperCase(Locale.ROOT) : PROTOCOL_OPENAI;
        if (!PROTOCOL_OPENAI.equals(protocol)) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "供应商协议 " + protocol + " 暂不支持，P5 仅支持 OPENAI 兼容协议");
        }
        if (!StringUtils.hasText(provider.getApiKey()) || !StringUtils.hasText(provider.getBaseUrl())
                || !StringUtils.hasText(config.getModelId())) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "tenantId=" + tenantId + " 的 Chat 凭证 / base_url / model_id 不完整");
        }
        return provider;
    }

    // ==================== 可取消调用（WebClient） ====================

    /**
     * 可取消路径：用 WebClient 发同样的非流式请求，通过 CompletableFuture 桥接保持 ModelClient 同步语义。
     *
     * <p>为什么不用 mono.block()：实测 block() 在下游 cancel 时不会立即解除阻塞（要等满超时），
     * 而 CompletableFuture.cancel(true) 能在毫秒级唤醒 get()。Reactor 的 toFuture() 在 cancel 时
     * 会取消上游订阅并断开 HTTP 连接，从而真正停止供应商生成。
     *
     * <p>取消时治理层会因 cancelToken.isCancelled() 短路，不走重试/降级。
     */
    private String invokeCancellable(Long tenantId, AiModelProvider provider, AiModelConfig config,
                                     Map<String, Object> body, int connectTimeoutMs, int readTimeoutMs,
                                     ChatCancelToken cancelToken) throws Exception {
        WebClient client = getOrCreateWebClient(tenantId, provider, config, connectTimeoutMs, readTimeoutMs);
        Mono<String> mono = client.post()
                .uri(COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class);
        java.util.concurrent.CompletableFuture<String> future = mono.toFuture();
        // 注册取消句柄：token.cancel() → future.cancel(true) → 唤醒 get + 取消 Reactor 订阅（断 socket）
        reactor.core.Disposable cancelHandle = () -> future.cancel(true);
        cancelToken.register(cancelHandle);
        try {
            return future.get(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.CancellationException ex) {
            // 用户取消：包装成可执行异常，由治理层的 cancelToken.isCancelled() 短路
            throw new IllegalStateException("请求已取消", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause);
        } catch (java.util.concurrent.TimeoutException ex) {
            throw new java.net.SocketTimeoutException("Chat 响应超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat 调用被中断", ex);
        } finally {
            // 正常完成时撤销订阅引用（cancelToken 已取消时列表已清空，remove 无副作用）
            cancelToken.unregister(cancelHandle);
        }
    }

    private WebClient getOrCreateWebClient(Long tenantId, AiModelProvider provider, AiModelConfig config,
                                           int connectTimeoutMs, int readTimeoutMs) {
        String fingerprint = fingerprint(provider, config);
        String cacheKey = tenantId + "|" + fingerprint + "|" + connectTimeoutMs + "|" + readTimeoutMs;
        return webClients.computeIfAbsent(cacheKey,
                key -> buildWebClient(provider, connectTimeoutMs, readTimeoutMs));
    }

    private static WebClient buildWebClient(AiModelProvider provider, int connectTimeoutMs, int readTimeoutMs) {
        // 用 WebClient 默认 connector（Spring AI starter 传递引入 Reactor Netty）。
        // 响应时间上限由 Mono.timeout(readTimeoutMs) 控制，连接超时由治理层 future.get 兜底，
        // 这里不直接引用 reactor.netty 的类（当前编译 classpath 未暴露它们）。
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(normalizeBaseUrl(provider.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        Map<String, String> headers = customHeaders(provider.getHeaders());
        headers.forEach(builder::defaultHeader);
        return builder.build();
    }

    // ==================== 客户端构造与缓存 ====================

    private RestClient getOrCreateClient(Long tenantId, AiModelProvider provider, AiModelConfig config,
                                         int connectTimeoutMs, int readTimeoutMs) {
        String fingerprint = fingerprint(provider, config);
        String cacheKey = tenantId + "|" + fingerprint + "|" + connectTimeoutMs + "|" + readTimeoutMs;
        String previous = clientKeyByTenant.put(tenantId, cacheKey);
        if (previous != null && !previous.equals(cacheKey)) {
            clients.remove(previous);
            log.info("[getOrCreateClient][tenantId={} Chat 配置变更，旧客户端失效 model={}]",
                    tenantId, config.getModelId());
        }
        return clients.computeIfAbsent(cacheKey, key -> buildClient(provider, connectTimeoutMs, readTimeoutMs));
    }

    private static RestClient buildClient(AiModelProvider provider, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(normalizeBaseUrl(provider.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                .requestFactory(factory);
        // ai_model_provider.headers 是 JSON 字符串，供需要额外鉴权头的兼容网关使用
        Map<String, String> headers = customHeaders(provider.getHeaders());
        headers.forEach(builder::defaultHeader);
        return builder.build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        // 兼容 base_url 已带 /v1 的供应商（OpenAI 官方、硅基流动等），避免拼成 /v1/v1/chat/completions
        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }
        return value;
    }

    private static Map<String, String> customHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return headers;
        }
        try {
            Map<String, String> map = JSON.readValue(raw, new TypeReference<Map<String, String>>() {
            });
            if (map != null) {
                headers.putAll(map);
            }
        } catch (Exception ex) {
            log.warn("[customHeaders][headers 不是合法 JSON 对象，已忽略] {}", ex.getMessage());
        }
        return headers;
    }

    private static String fingerprint(AiModelProvider provider, AiModelConfig config) {
        String raw = provider.getBaseUrl() + "\n" + provider.getApiKey() + "\n"
                + provider.getHeaders() + "\n" + config.getModelId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算 Chat 凭证指纹", ex);
        }
    }

    // ==================== 错误分类 ====================

    /** 把 HTTP / IO 异常精确分类，供治理层做重试 / 熔断决策 */
    private static ModelErrorCategory classify(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ModelException me) {
                return me.getCategory();
            }
            if (cur instanceof HttpStatusCodeException hse) {
                return byStatus(hse.getStatusCode());
            }
            // 流式走 WebClient，其异常与 RestClient 的 HttpStatusCodeException 不是同一体系，
            // 不单独判断会落到按消息关键字兜底（把 400 误判成 SERVER_ERROR 并触发重试）
            if (cur instanceof WebClientResponseException wcre) {
                return byStatus(wcre.getStatusCode());
            }
            if (cur instanceof java.util.concurrent.TimeoutException
                    || cur instanceof java.net.SocketTimeoutException) {
                return ModelErrorCategory.TIMEOUT;
            }
            if (cur instanceof ResourceAccessException) {
                // 连接失败 / DNS / TLS 握手失败
                return ModelErrorCategory.CONNECT;
            }
            cur = cur.getCause();
            if (cur == ex) {
                break;
            }
        }
        return ModelErrorCategory.from(ex);
    }

    private static ModelErrorCategory byStatus(HttpStatusCode status) {
        int code = status == null ? 0 : status.value();
        if (code == 429) {
            return ModelErrorCategory.RATE_LIMIT;
        }
        if (code == 401 || code == 403) {
            return ModelErrorCategory.AUTH;
        }
        if (code == 400 || code == 422) {
            return ModelErrorCategory.BAD_REQUEST;
        }
        if (code == 408 || code == 504) {
            return ModelErrorCategory.TIMEOUT;
        }
        if (code >= 500) {
            return ModelErrorCategory.SERVER_ERROR;
        }
        return ModelErrorCategory.BAD_REQUEST;
    }

    // ==================== 小工具 ====================

    private static double defaultTemperature(AiModelConfig config) {
        if (!StringUtils.hasText(config.getDefaultParams())) {
            return 0.0d;
        }
        try {
            Map<String, Object> params = JSON.readValue(config.getDefaultParams(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object value = params == null ? null : params.get("temperature");
            if (value instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ignored) {
            // 非法 JSON 不影响调用
        }
        return 0.0d;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int property(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
