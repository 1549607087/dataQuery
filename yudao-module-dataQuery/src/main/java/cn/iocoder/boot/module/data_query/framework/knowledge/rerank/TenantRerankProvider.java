package cn.iocoder.boot.module.data_query.framework.knowledge.rerank;

import cn.iocoder.boot.module.data_query.entity.AiModelCallLog;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelProvider;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.framework.model.ModelCircuitBreaker;
import cn.iocoder.boot.module.data_query.framework.model.ModelErrorCategory;
import cn.iocoder.boot.module.data_query.framework.model.ModelRateLimiter;
import cn.iocoder.boot.module.data_query.service.AiModelCallLogService;
import cn.iocoder.boot.module.data_query.service.AiModelConfigService;
import cn.iocoder.boot.module.data_query.service.AiModelProviderService;
import cn.iocoder.boot.module.data_query.service.AiModelSceneBindingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_BINDING_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_CALL_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_CIRCUIT_OPEN;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_CONFIG_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_CREDENTIAL_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_PROTOCOL_UNSUPPORTED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_PROVIDER_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_RERANK_RATE_LIMITED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 按租户动态解析的 Rerank 模型服务 —— 知识库召回 Top-K 的 Cross-Encoder 精排实现。
 *
 * <p>口径与 {@code TenantSchemaEmbeddingProvider} 完全一致（同属「非 chat 语义模型」）：
 * <ol>
 *   <li>每租户独立：RERANK 场景绑定 → model_type=3 的本租户配置 → 本租户 ai_model_provider
 *       （apiKey 由 AiSecretTypeHandler 解密）→ 专用 rerank HTTP 接口。绝不用全局 Key。</li>
 *   <li>治理复用：QPS / 并发限流、熔断、ai_model_call_log 全部落本租户；
 *       限流/熔断键带 "RERANK:" 前缀，与 Chat / Embedding 隔离。</li>
 *   <li>配置即时生效：每次调用都查库解析，不做 Resolved 缓存；只按凭证指纹缓存 RestClient。</li>
 *   <li>失败不伪造分数：任一环节失败都抛带可读错误码的异常，由召回侧吞掉降级回 RRF 原序。</li>
 * </ol>
 *
 * <p>协议自适应（{@code rerank.protocol}）：
 * <ul>
 *   <li>DASHSCOPE（默认）：POST {base}/api/v1/services/rerank/text-rerank/text-rerank，
 *       body {@code {"model":M,"input":{"query":Q,"documents":[...],"top_n":N}}}，
 *       响应 {@code output.results[].relevance_score}；</li>
 *   <li>COHERE：POST {base}/v1/rerank，body {@code {"model":M,"query":Q,"documents":[...],"top_n":N}}，
 *       响应 {@code results[].relevance_score}。</li>
 * </ul>
 * baseUrl 不做剥 /v1 处理：要求后台把 base_url 配到 host 根，path 单独配完整路径
 * （dashscope 的专用路径不是 /v1/*，剥 /v1 会拼错）。
 *
 * <p>本类为 {@code @Component} 常驻 Bean。是否已固化启用由召回侧无条件调用，未绑定模型时
 * isAvailable() 返回 false 直接降级回 RRF 原序，确保未配置租户零副作用。
 */
@Slf4j
@Component
public class TenantRerankProvider implements KnowledgeRerankProvider {

    private static final String SCENE_CODE = "RERANK";
    private static final int MODEL_TYPE_RERANK = 3;
    private static final List<Integer> CONFIG_STATUS_USABLE = Arrays.asList(0, 2);
    private static final String SCENE_KEY_PREFIX = "RERANK:";
    private static final String PENDING_MODEL_CODE = "PENDING";
    private static final String PROTOCOL_DASHSCOPE = "DASHSCOPE";
    private static final String PROTOCOL_COHERE = "COHERE";
    private static final String DEFAULT_PATH_DASHSCOPE = "/api/v1/services/rerank/text-rerank/text-rerank";
    private static final String DEFAULT_PATH_COHERE = "/v1/rerank";
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private AiModelSceneBindingService sceneBindingService;
    @Resource
    private AiModelConfigService modelConfigService;
    @Resource
    private AiModelProviderService modelProviderService;
    @Resource
    private AiModelCallLogService callLogService;
    @Resource
    private ModelRateLimiter rateLimiter;
    @Resource
    private ModelCircuitBreaker circuitBreaker;

    @Value("${data-query.knowledge.rerank.timeout-ms:1500}")
    private int configuredTimeoutMs = DEFAULT_TIMEOUT_MS;
    @Value("${data-query.knowledge.rerank.connect-timeout-ms:2000}")
    private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
    @Value("${data-query.knowledge.rerank.protocol:DASHSCOPE}")
    private String protocol = PROTOCOL_DASHSCOPE;
    @Value("${data-query.knowledge.rerank.rerank-path:}")
    private String rerankPath = "";
    @Value("${data-query.knowledge.rerank.top-n:0}")
    private int topN = 0;

    /** 客户端缓存：key = tenantId|fingerprint；换凭证 / 换模型 / 换超时立即失效 */
    private final ConcurrentHashMap<String, RestClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> clientKeyByTenant = new ConcurrentHashMap<>();

    @Override
    public boolean isAvailable(Long tenantId) {
        return resolve(tenantId, false) != null;
    }

    @Override
    public String currentModelCode(Long tenantId) {
        Resolved resolved = resolve(tenantId, false);
        return resolved == null ? PENDING_MODEL_CODE : resolved.modelCode;
    }

    @Override
    public RerankResult rerank(Long tenantId, String query, List<String> documents) {
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Resolved resolved = resolve(tenantId, true);
        String effectiveProtocol = protocolName(resolved.provider);
        String path = resolvePath(effectiveProtocol);

        // 显式租户号拼限流键，不依赖 ThreadLocal
        String keyBase = SCENE_KEY_PREFIX + resolved.config.getProviderId() + ":" + resolved.config.getId();
        String limiterKey = tenantId + ":" + keyBase;
        // 让熔断器的 circuitKey(config) 带 RERANK 前缀，与 Chat / Embedding 键隔离
        resolved.config.setCircuitKey(keyBase);

        int qpsLimit = nvl(resolved.config.getQpsLimit());
        int concurrentLimit = nvl(resolved.config.getConcurrentLimit());
        // 硬超时：取「场景配置」与「模型配置」中更小者，确保热路径不被拖垮
        int timeoutMs = Math.min(positive(configuredTimeoutMs, DEFAULT_TIMEOUT_MS),
                positive(resolved.config.getTimeoutMs(), DEFAULT_TIMEOUT_MS));
        long begin = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        if (!rateLimiter.tryAcquireQps(limiterKey, qpsLimit)) {
            writeLog(resolved, tenantId, traceId, 1, elapsed(begin), limiterKey,
                    ModelErrorCategory.LOCAL_RATE_LIMIT, "本地 QPS 限流");
            throw exception(KNOWLEDGE_RERANK_RATE_LIMITED);
        }
        if (!rateLimiter.tryAcquireConcurrency(limiterKey, concurrentLimit, timeoutMs)) {
            writeLog(resolved, tenantId, traceId, 1, elapsed(begin), limiterKey,
                    ModelErrorCategory.QUEUE_FULL, "并发队列已满");
            throw exception(KNOWLEDGE_RERANK_RATE_LIMITED);
        }
        try {
            if (!circuitBreaker.allowRequest(resolved.config)) {
                writeLog(resolved, tenantId, traceId, 3, elapsed(begin), limiterKey,
                        ModelErrorCategory.CIRCUIT_OPEN, "Rerank 熔断已打开");
                throw exception(KNOWLEDGE_RERANK_CIRCUIT_OPEN);
            }
            RestClient client = getOrCreateClient(tenantId, resolved, effectiveProtocol, timeoutMs);
            Map<String, Object> body = buildBody(resolved.config.getModelId(), query, documents, effectiveProtocol);
            String raw = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            double[] scores = parseResponse(raw, documents.size(), effectiveProtocol);
            circuitBreaker.recordSuccess(resolved.config);
            writeLog(resolved, tenantId, traceId, 0, elapsed(begin), limiterKey, null, null);

            RerankResult result = new RerankResult();
            result.setScores(scores);
            result.setModelCode(resolved.modelCode);
            result.setLatencyMs(elapsed(begin));
            return result;
        } catch (Exception ex) {
            ModelErrorCategory category = classify(ex);
            circuitBreaker.recordFailure(resolved.config, category, category.name());
            writeLog(resolved, tenantId, traceId, category == ModelErrorCategory.TIMEOUT ? 2 : 1,
                    elapsed(begin), limiterKey, category, ex.getMessage());
            log.warn("[rerank][调用失败 tenantId={} model={} docs={}] {}",
                    tenantId, resolved.modelCode, documents.size(), ex.toString());
            throw exception(KNOWLEDGE_RERANK_CALL_FAILED);
        } finally {
            rateLimiter.releaseConcurrency(limiterKey, concurrentLimit);
        }
    }

    // ==================== 配置解析：每次调用都查库，配置变更立即生效 ====================

    private Resolved resolve(Long tenantId, boolean strict) {
        if (tenantId == null) {
            if (strict) {
                throw exception(TENANT_CONTEXT_MISSING);
            }
            return null;
        }
        // 全部显式带 tenant_id：即使租户拦截器被误放行，也拿不到别的租户凭证
        List<AiModelSceneBinding> bindings = sceneBindingService.list(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<AiModelSceneBinding>()
                        .eq(AiModelSceneBinding::getSceneCode, SCENE_CODE)
                        .eq(AiModelSceneBinding::getEnabled, 1)
                        .eq(AiModelSceneBinding::getTenantId, tenantId)
                        .eq(AiModelSceneBinding::getDeleted, 0)
                        .orderByAsc(AiModelSceneBinding::getSort)
                        .last("LIMIT 1"));
        if (bindings.isEmpty() || bindings.get(0).getPrimaryModelId() == null
                || bindings.get(0).getPrimaryModelId() <= 0) {
            return absent(strict, KNOWLEDGE_RERANK_BINDING_MISSING,
                    "tenantId=" + tenantId + " 未配置启用的 RERANK 场景绑定");
        }

        List<AiModelConfig> configs = modelConfigService.list(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<AiModelConfig>()
                        .eq(AiModelConfig::getId, bindings.get(0).getPrimaryModelId())
                        .eq(AiModelConfig::getTenantId, tenantId)
                        .eq(AiModelConfig::getModelType, MODEL_TYPE_RERANK)
                        .in(AiModelConfig::getStatus, CONFIG_STATUS_USABLE)
                        .eq(AiModelConfig::getDeleted, 0)
                        .last("LIMIT 1"));
        if (configs.isEmpty()) {
            return absent(strict, KNOWLEDGE_RERANK_CONFIG_MISSING,
                    "tenantId=" + tenantId + " 的 RERANK 绑定未指向可用的 model_type=3 配置");
        }
        AiModelConfig config = configs.get(0);

        List<AiModelProvider> providers = modelProviderService.list(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<AiModelProvider>()
                        .eq(AiModelProvider::getId, config.getProviderId())
                        .eq(AiModelProvider::getTenantId, tenantId)
                        .eq(AiModelProvider::getDeleted, 0)
                        .last("LIMIT 1"));
        if (providers.isEmpty()) {
            return absent(strict, KNOWLEDGE_RERANK_PROVIDER_MISSING,
                    "tenantId=" + tenantId + " 的 Rerank 配置未关联本租户供应商");
        }
        AiModelProvider provider = providers.get(0);

        String effectiveProtocol = protocolName(provider);
        if (!PROTOCOL_DASHSCOPE.equals(effectiveProtocol) && !PROTOCOL_COHERE.equals(effectiveProtocol)) {
            return absent(strict, KNOWLEDGE_RERANK_PROTOCOL_UNSUPPORTED,
                    "tenantId=" + tenantId + " 的 Rerank 协议 " + effectiveProtocol + " 暂不支持");
        }
        if (!StringUtils.hasText(provider.getApiKey()) || !StringUtils.hasText(provider.getBaseUrl())
                || !StringUtils.hasText(config.getModelId())) {
            return absent(strict, KNOWLEDGE_RERANK_CREDENTIAL_MISSING,
                    "tenantId=" + tenantId + " 的 Rerank 凭证 / base_url / model_id 不完整");
        }

        String modelCode = (StringUtils.hasText(provider.getCode()) ? provider.getCode() : "openai")
                + ":" + config.getModelId();
        String fingerprint = fingerprint(provider, config, modelCode, effectiveProtocol);
        return new Resolved(config, provider, modelCode, fingerprint);
    }

    private Resolved absent(boolean strict, cn.iocoder.yudao.framework.common.exception.ErrorCode code, String detail) {
        log.debug("[resolve][Rerank 不可用] {}", detail);
        if (strict) {
            throw exception(code);
        }
        return null;
    }

    /** 供应商协议优先，未配置则回退全局配置 */
    private String protocolName(AiModelProvider provider) {
        String value = StringUtils.hasText(provider.getProtocol())
                ? provider.getProtocol().trim().toUpperCase(Locale.ROOT) : protocol;
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolvePath(String effectiveProtocol) {
        String configured = rerankPath == null ? "" : rerankPath.trim();
        if (!configured.isEmpty()) {
            return configured.startsWith("/") ? configured : "/" + configured;
        }
        return PROTOCOL_COHERE.equals(effectiveProtocol) ? DEFAULT_PATH_COHERE : DEFAULT_PATH_DASHSCOPE;
    }

    // ==================== 请求构造 ====================

    private Map<String, Object> buildBody(String modelId, String query, List<String> documents, String effectiveProtocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        if (PROTOCOL_DASHSCOPE.equals(effectiveProtocol)) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", documents);
            if (topN > 0) {
                input.put("top_n", topN);
            }
            body.put("input", input);
        } else {
            body.put("query", query);
            body.put("documents", documents);
            if (topN > 0) {
                body.put("top_n", topN);
            }
        }
        return body;
    }

    // ==================== 响应解析：按 index 归位到 documents 原序 ====================

    double[] parseResponse(String raw, int expected, String effectiveProtocol) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("Rerank 响应正文为空");
        }
        JsonNode root;
        try {
            root = JSON.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Rerank 响应不是合法 JSON");
        }
        if (root.has("error") && !root.get("error").isNull()) {
            String msg = root.path("error").path("message").asText(root.path("error").toString());
            throw new IllegalStateException("供应商返回错误：" + abbreviate(msg, 200));
        }
        JsonNode results = null;
        if (root.path("output").path("results").isArray()) {
            results = root.path("output").path("results");
        } else if (root.path("results").isArray()) {
            results = root.path("results");
        }
        if (results == null) {
            throw new IllegalStateException("Rerank 响应无 results 数组(protocol=" + effectiveProtocol + ")");
        }

        double[] scores = new double[expected];
        Arrays.fill(scores, Double.NaN);
        boolean[] seen = new boolean[expected];
        for (int i = 0; i < results.size(); i++) {
            JsonNode item = results.get(i);
            int idx = item.hasNonNull("index") ? item.path("index").asInt() : i;
            JsonNode scoreNode = item.has("relevance_score") ? item.get("relevance_score") : item.get("score");
            double score = scoreNode == null || scoreNode.isNull() ? Double.NaN : scoreNode.asDouble(Double.NaN);
            if (idx >= 0 && idx < expected && !seen[idx] && Double.isFinite(score)) {
                scores[idx] = score;
                seen[idx] = true;
            }
        }
        for (boolean b : seen) {
            if (!b) {
                throw new IllegalStateException("Rerank 返回分数缺失或数量不符：expected=" + expected);
            }
        }
        return scores;
    }

    // ==================== 客户端构造与缓存 ====================

    private RestClient getOrCreateClient(Long tenantId, Resolved resolved, String effectiveProtocol, int timeoutMs) {
        String cacheKey = tenantId + "|" + resolved.fingerprint + "|" + effectiveProtocol + "|" + timeoutMs;
        String previous = clientKeyByTenant.put(tenantId, cacheKey);
        if (previous != null && !previous.equals(cacheKey)) {
            clients.remove(previous);
            log.info("[getOrCreateClient][tenantId={} Rerank 配置变更，旧客户端失效 model={}]",
                    tenantId, resolved.modelCode);
        }
        return clients.computeIfAbsent(cacheKey,
                key -> buildClient(resolved.provider, timeoutMs));
    }

    private RestClient buildClient(AiModelProvider provider, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(timeoutMs);
        String base = provider.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                .requestFactory(factory);
        customHeaders(provider.getHeaders()).forEach(builder::defaultHeader);
        return builder.build();
    }

    private static Map<String, String> customHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return headers;
        }
        try {
            Map<String, String> parsed = JSON.readValue(raw,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
            if (parsed != null) {
                headers.putAll(parsed);
            }
        } catch (Exception ex) {
            log.warn("[customHeaders][headers 不是合法 JSON 对象，已忽略] {}", ex.getMessage());
        }
        return headers;
    }

    private String fingerprint(AiModelProvider provider, AiModelConfig config, String modelCode, String effectiveProtocol) {
        String raw = provider.getBaseUrl() + "\n" + provider.getApiKey() + "\n"
                + provider.getHeaders() + "\n" + modelCode + "\n" + effectiveProtocol;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算 Rerank 凭证指纹", ex);
        }
    }

    // ==================== 错误分类 ====================

    private static ModelErrorCategory classify(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof HttpStatusCodeException hse) {
                int code = hse.getStatusCode().value();
                if (code == 429) {
                    return ModelErrorCategory.RATE_LIMIT;
                }
                if (code == 401 || code == 403) {
                    return ModelErrorCategory.AUTH;
                }
                if (code == 408 || code == 504) {
                    return ModelErrorCategory.TIMEOUT;
                }
                if (code >= 500) {
                    return ModelErrorCategory.SERVER_ERROR;
                }
                return ModelErrorCategory.BAD_REQUEST;
            }
            if (cur instanceof java.util.concurrent.TimeoutException
                    || cur instanceof java.net.SocketTimeoutException) {
                return ModelErrorCategory.TIMEOUT;
            }
            if (cur instanceof ResourceAccessException) {
                return ModelErrorCategory.CONNECT;
            }
            cur = cur.getCause();
            if (cur == ex) {
                break;
            }
        }
        return ModelErrorCategory.from(ex);
    }

    // ==================== 调用日志 ====================

    private void writeLog(Resolved resolved, Long tenantId, String traceId, int status,
                          int latencyMs, String circuitKey,
                          ModelErrorCategory category, String errorMsg) {
        try {
            AiModelCallLog row = new AiModelCallLog();
            row.setTraceId(traceId);
            row.setConversationId(0L);
            row.setSceneCode(SCENE_CODE);
            row.setProviderId(resolved.config.getProviderId());
            row.setModelConfigId(resolved.config.getId());
            row.setModelId(resolved.config.getModelId());
            row.setRequestTokens(0);
            row.setResponseTokens(0);
            row.setTotalTokens(0);
            row.setCost(BigDecimal.ZERO);
            row.setLatencyMs(latencyMs);
            row.setStatus(status); // 0 成功 / 1 失败 / 2 超时 / 3 降级
            row.setFallbackFromModelId(0L);
            row.setErrorCode(category == null ? "" : category.name());
            row.setErrorMsg(errorMsg == null ? "" : errorMsg.substring(0, Math.min(500, errorMsg.length())));
            row.setErrorCategory(category == null ? "" : category.name());
            row.setIsGray(0);
            row.setGrayGroup("");
            row.setGrayExperimentId(0L);
            row.setGrayBucketKey("");
            row.setRetryOfTraceId("");
            row.setRetryIndex(0);
            row.setCircuitState(circuitBreaker.currentState(circuitKey));
            row.setCircuitKey(circuitKey);
            row.setFallbackDepth(0);
            row.setRouteStrategy(1);
            row.setQueueWaitMs(0);
            row.setRequestId(traceId);
            row.setCreator("knowledge-rerank");
            row.setUpdater("knowledge-rerank");
            LocalDateTime now = LocalDateTime.now();
            row.setCreateTime(now);
            row.setUpdateTime(now);
            row.setDeleted(0);
            // 私有表 INSERT 不会被 DefaultDBFieldHandler 自动填充，必须显式写 tenant_id
            row.setTenantId(tenantId);
            callLogService.save(row);
        } catch (Exception ex) {
            log.warn("[writeLog][Rerank 调用日志写入失败 tenantId={} traceId={}] {}",
                    tenantId, traceId, ex.toString());
        }
    }

    // ==================== 工具 ====================

    private static int elapsed(long begin) {
        return (int) (System.currentTimeMillis() - begin);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 一次解析结果（不缓存：启停 / 换 Key / 换模型必须立即生效） */
    private static final class Resolved {
        private final AiModelConfig config;
        private final AiModelProvider provider;
        private final String modelCode;
        private final String fingerprint;

        Resolved(AiModelConfig config, AiModelProvider provider, String modelCode, String fingerprint) {
            this.config = config;
            this.provider = provider;
            this.modelCode = modelCode;
            this.fingerprint = fingerprint;
        }
    }
}
