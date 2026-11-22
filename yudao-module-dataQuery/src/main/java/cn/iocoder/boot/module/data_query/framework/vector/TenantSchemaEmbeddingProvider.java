package cn.iocoder.boot.module.data_query.framework.vector;

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
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_BINDING_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_CALL_FAILED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_CIRCUIT_OPEN;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_CONFIG_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_CREDENTIAL_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_PROTOCOL_UNSUPPORTED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_PROVIDER_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_EMBEDDING_RATE_LIMITED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.SCHEMA_VECTOR_DIM_MISMATCH;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 按租户动态解析的 Embedding 服务（P2）——Schema 向量写入侧的真实实现。
 *
 * 口径（与 docs/intelligent-query-flow.md 修订三一致）：
 * 1. 账号 / 凭证 / 调用归属【每租户独立】：EMBEDDING 场景绑定 → model_type = 2 的本租户配置
 *    → 本租户 ai_model_provider（凭证经 AiSecretTypeHandler 解密）→ OpenAI 兼容 embeddings 接口。
 *    绝不使用 spring.ai.openai.api-key 那个全局 Bean，也不设置平台共享 Key。
 * 2. 维度统一 1536 只是 vector(1536) 列 / HNSW 索引的技术约束，不等于账号统一。
 *    modelCode = provider.code:model_id:dimension 参与 content_hash，换供应商 / 模型 / 维度
 *    会自动触发【仅该租户】的向量增量重建。
 * 3. 治理复用 M1：QPS / 并发限流、熔断、ai_model_call_log 全部落到本租户；
 *    熔断与限流键带 "EMBEDDING:" 场景前缀，与租户的 Chat / SQL 生成键互不干扰。
 * 4. 失败不伪造向量：任一环节失败都抛出可读异常，由写入侧任务记为失败并保留
 *    sync_status = 0 的待构建行，下次重跑补齐。
 *
 * 为什么不用 spring-ai-starter-vector-store-pgvector：
 * 该 starter 在离线仓库只有 2.0.1/2.0.2，项目 BOM 是 1.1.8；且 PgVectorStore 绑定单个
 * EmbeddingModel Bean、走 JdbcTemplate 绕过 MyBatis 多租户拦截器，与「每租户动态模型 +
 * 向量表全租户私有」直接冲突。这里只借用 Spring AI 1.1.8 的 OpenAiEmbeddingModel 做 HTTP
 * 调用，向量落库仍走 ai_schema_vector 的自定义 Mapper + PgVectorTypeHandler。
 *
 * 配置：data-query.embedding.enabled = false 可立即回退 P1 的「待构建」占位实现，
 * 用于供应商故障时的运维降级，不需要改代码或重启后改数据库。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data-query.embedding", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TenantSchemaEmbeddingProvider implements SchemaEmbeddingProvider {

    private static final String SCENE_CODE = "EMBEDDING";
    private static final int MODEL_TYPE_EMBEDDING = 2;
    private static final List<Integer> CONFIG_STATUS_USABLE = Arrays.asList(0, 2);
    private static final String PROTOCOL_OPENAI = "OPENAI";
    private static final String PENDING_MODEL_CODE = "PENDING";
    private static final String SCENE_KEY_PREFIX = "EMBEDDING:";
    private static final int DEFAULT_TIMEOUT_MS = 60_000;
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

    /** 必须与 ai_schema_vector.embedding 的 vector(1536) 列一致 */
    @Value("${data-query.embedding.dimension:1536}")
    private int dimension = 1536;
    @Value("${data-query.embedding.connect-timeout-ms:5000}")
    private int connectTimeoutMs = 5000;
    @Value("${data-query.embedding.read-timeout-ms:30000}")
    private int readTimeoutMs = 30000;
    /** 部分兼容供应商不接受 dimensions 参数，可关闭（仍由写入侧强校验向量维度） */
    @Value("${data-query.embedding.send-dimensions:true}")
    private boolean sendDimensions = true;

    /** 客户端缓存：(tenantId, 凭证指纹) -> EmbeddingModel；换 Key / 换供应商 / 换模型自动失效 */
    private final ConcurrentHashMap<String, OpenAiEmbeddingModel> clients = new ConcurrentHashMap<>();
    /** 每个租户当前使用的缓存键，用于换配置时清理旧客户端，避免凭证长期驻留内存 */
    private final ConcurrentHashMap<Long, String> clientKeyByTenant = new ConcurrentHashMap<>();

    @Override
    public int dimension() {
        return dimension;
    }

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
    public List<String> embed(Long tenantId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        Resolved resolved = resolve(tenantId, true);
        // 显式租户号拼限流键，不依赖 ThreadLocal；异步索引任务同样安全
        String sceneBase = resolved.config.getProviderId() + ":" + resolved.config.getId();
        String limiterKey = tenantId + ":" + SCENE_KEY_PREFIX + sceneBase;
        // 让 ModelCircuitBreaker 内部的 circuitKey(config) 带上场景前缀，与 Chat / SQL 键隔离
        resolved.config.setCircuitKey(SCENE_KEY_PREFIX + sceneBase);

        int qpsLimit = nvl(resolved.config.getQpsLimit());
        int concurrentLimit = nvl(resolved.config.getConcurrentLimit());
        int timeoutMs = resolved.config.getTimeoutMs() == null || resolved.config.getTimeoutMs() <= 0
                ? DEFAULT_TIMEOUT_MS : resolved.config.getTimeoutMs();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long begin = System.currentTimeMillis();

        if (!rateLimiter.tryAcquireQps(limiterKey, qpsLimit)) {
            writeLog(resolved, tenantId, traceId, 1, elapsed(begin), 0, limiterKey,
                    ModelErrorCategory.LOCAL_RATE_LIMIT, "本地 QPS 限流");
            throw exception(SCHEMA_EMBEDDING_RATE_LIMITED);
        }
        if (!rateLimiter.tryAcquireConcurrency(limiterKey, concurrentLimit, timeoutMs)) {
            writeLog(resolved, tenantId, traceId, 1, elapsed(begin), 0, limiterKey,
                    ModelErrorCategory.QUEUE_FULL, "并发队列已满");
            throw exception(SCHEMA_EMBEDDING_RATE_LIMITED);
        }
        try {
            if (!circuitBreaker.allowRequest(resolved.config)) {
                writeLog(resolved, tenantId, traceId, 1, elapsed(begin), 0, limiterKey,
                        ModelErrorCategory.CIRCUIT_OPEN, "Embedding 熔断已打开");
                throw exception(SCHEMA_EMBEDDING_CIRCUIT_OPEN);
            }
            OpenAiEmbeddingModel model = getOrCreateClient(tenantId, resolved);
            EmbeddingResponse response = model.embedForResponse(texts);
            List<String> literals = toLiterals(response, texts.size());
            circuitBreaker.recordSuccess(resolved.config);
            int tokens = usageTokens(response);
            writeLog(resolved, tenantId, traceId, 0, elapsed(begin), tokens, limiterKey, null, null);
            return literals;
        } catch (DimensionMismatchException ex) {
            // 配置 / 供应商返回维度不符：属于配置错误，不算下游故障，不触发熔断
            writeLog(resolved, tenantId, traceId, 1, elapsed(begin), 0, limiterKey,
                    ModelErrorCategory.CONFIG_ERROR, ex.getMessage());
            throw exception(SCHEMA_VECTOR_DIM_MISMATCH);
        } catch (Exception ex) {
            ModelErrorCategory category = ModelErrorCategory.from(ex);
            circuitBreaker.recordFailure(resolved.config, category, category.name());
            writeLog(resolved, tenantId, traceId, 1, elapsed(begin), 0, limiterKey, category, ex.getMessage());
            log.warn("[embed][Embedding 调用失败 tenantId={} model={} texts={}] {}",
                    tenantId, resolved.modelCode, texts.size(), ex.toString());
            throw exception(SCHEMA_EMBEDDING_CALL_FAILED);
        } finally {
            rateLimiter.releaseConcurrency(limiterKey, concurrentLimit);
        }
    }

    // ==================== 配置解析：每次调用都查库，配置变更立即生效 ====================

    /**
     * 解析本租户的 EMBEDDING 模型链路。
     *
     * @param strict false 用于 isAvailable / currentModelCode 的探测（缺配置返回 null，保持 P1 待构建状态）；
     *               true 用于真正发起调用（缺配置抛可读错误码，绝不静默伪造向量）
     */
    private Resolved resolve(Long tenantId, boolean strict) {
        if (tenantId == null) {
            if (strict) {
                throw exception(TENANT_CONTEXT_MISSING);
            }
            return null;
        }
        // 全部显式带 tenant_id：即使租户拦截器被误放行，也拿不到别的租户的凭证
        List<AiModelSceneBinding> bindings = sceneBindingService.list(
                new LambdaQueryWrapperX<AiModelSceneBinding>()
                        .eq(AiModelSceneBinding::getSceneCode, SCENE_CODE)
                        .eq(AiModelSceneBinding::getEnabled, 1)
                        .eq(AiModelSceneBinding::getTenantId, tenantId)
                        .eq(AiModelSceneBinding::getDeleted, 0)
                        .orderByAsc(AiModelSceneBinding::getSort)
                        .last("LIMIT 1"));
        if (bindings.isEmpty() || bindings.get(0).getPrimaryModelId() == null) {
            return absent(strict, SCHEMA_EMBEDDING_BINDING_MISSING,
                    "tenantId=" + tenantId + " 未配置启用的 EMBEDDING 场景绑定");
        }

        List<AiModelConfig> configs = modelConfigService.list(
                new LambdaQueryWrapperX<AiModelConfig>()
                        .eq(AiModelConfig::getId, bindings.get(0).getPrimaryModelId())
                        .eq(AiModelConfig::getTenantId, tenantId)
                        .eq(AiModelConfig::getModelType, MODEL_TYPE_EMBEDDING)
                        .in(AiModelConfig::getStatus, CONFIG_STATUS_USABLE)
                        .eq(AiModelConfig::getDeleted, 0)
                        .last("LIMIT 1"));
        if (configs.isEmpty()) {
            return absent(strict, SCHEMA_EMBEDDING_CONFIG_MISSING,
                    "tenantId=" + tenantId + " 的 EMBEDDING 绑定未指向可用的 model_type=2 配置");
        }
        AiModelConfig config = configs.get(0);

        List<AiModelProvider> providers = modelProviderService.list(
                new LambdaQueryWrapperX<AiModelProvider>()
                        .eq(AiModelProvider::getId, config.getProviderId())
                        .eq(AiModelProvider::getTenantId, tenantId)
                        .eq(AiModelProvider::getDeleted, 0)
                        .last("LIMIT 1"));
        if (providers.isEmpty()) {
            return absent(strict, SCHEMA_EMBEDDING_PROVIDER_MISSING,
                    "tenantId=" + tenantId + " 的 Embedding 配置未关联本租户供应商");
        }
        AiModelProvider provider = providers.get(0);

        String protocol = StringUtils.hasText(provider.getProtocol())
                ? provider.getProtocol().trim().toUpperCase() : PROTOCOL_OPENAI;
        if (!PROTOCOL_OPENAI.equals(protocol)) {
            return absent(strict, SCHEMA_EMBEDDING_PROTOCOL_UNSUPPORTED,
                    "tenantId=" + tenantId + " 的供应商协议 " + protocol + " 暂不支持，P2 仅支持 OPENAI 兼容协议");
        }
        if (!StringUtils.hasText(provider.getApiKey())
                || !StringUtils.hasText(provider.getBaseUrl())
                || !StringUtils.hasText(config.getModelId())) {
            return absent(strict, SCHEMA_EMBEDDING_CREDENTIAL_MISSING,
                    "tenantId=" + tenantId + " 的 Embedding 凭证 / base_url / model_id 不完整");
        }

        String modelCode = (StringUtils.hasText(provider.getCode()) ? provider.getCode() : "openai")
                + ":" + config.getModelId() + ":" + dimension;
        String fingerprint = fingerprint(provider, config, modelCode);
        return new Resolved(config, provider, modelCode, fingerprint);
    }

    private Resolved absent(boolean strict, ErrorCode code, String detail) {
        log.debug("[resolve][Embedding 不可用] {}", detail);
        if (strict) {
            throw exception(code);
        }
        return null;
    }

    // ==================== 客户端构造与缓存 ====================

    private OpenAiEmbeddingModel getOrCreateClient(Long tenantId, Resolved resolved) {
        String cacheKey = tenantId + "|" + resolved.fingerprint;
        String previous = clientKeyByTenant.put(tenantId, cacheKey);
        if (previous != null && !previous.equals(cacheKey)) {
            // 凭证轮换 / 换模型：立即丢弃旧客户端，避免旧 Key 继续驻留内存
            clients.remove(previous);
            log.info("[getOrCreateClient][tenantId={} Embedding 配置已变更，旧客户端失效 model={}]",
                    tenantId, resolved.modelCode);
        }
        return clients.computeIfAbsent(cacheKey, key -> buildClient(resolved));
    }

    private OpenAiEmbeddingModel buildClient(Resolved resolved) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(resolved.provider.getBaseUrl()))
                .apiKey(resolved.provider.getApiKey())
                .headers(customHeaders(resolved.provider.getHeaders()))
                .restClientBuilder(RestClient.builder().requestFactory(factory))
                .build();
        OpenAiEmbeddingOptions.Builder options = OpenAiEmbeddingOptions.builder()
                .model(resolved.config.getModelId());
        if (sendDimensions && dimension > 0) {
            options.dimensions(dimension);
        }
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options.build());
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        // OpenAiApi.Builder 默认 embeddingsPath = /v1/embeddings，
        // base_url 已带 /v1 时必须剥掉，否则拼成 /v1/v1/embeddings 触发 404
        if (value.endsWith("/v1/embeddings")) {
            value = value.substring(0, value.length() - "/v1/embeddings".length());
        } else if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }
        return value;
    }

    /** ai_model_provider.headers 是 JSON 字符串，供需要额外鉴权头的兼容网关使用 */
    private static MultiValueMap<String, String> customHeaders(String raw) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (!StringUtils.hasText(raw)) {
            return headers;
        }
        try {
            Map<String, String> map = JSON.readValue(raw, new TypeReference<Map<String, String>>() {
            });
            map.forEach(headers::add);
        } catch (Exception ex) {
            log.warn("[customHeaders][headers 不是合法 JSON 对象，已忽略] {}", ex.getMessage());
        }
        return headers;
    }

    private String fingerprint(AiModelProvider provider, AiModelConfig config, String modelCode) {
        String raw = provider.getBaseUrl() + "\n" + provider.getApiKey() + "\n"
                + provider.getHeaders() + "\n" + modelCode;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算 Embedding 凭证指纹", ex);
        }
    }

    // ==================== 响应转换 ====================

    private List<String> toLiterals(EmbeddingResponse response, int expected) {
        List<Embedding> results = response == null ? null : response.getResults();
        if (results == null || results.size() != expected) {
            throw new IllegalStateException("Embedding 返回数量与输入不一致：expected=" + expected
                    + " actual=" + (results == null ? 0 : results.size()));
        }
        List<String> literals = new ArrayList<>(expected);
        for (Embedding embedding : results) {
            float[] vector = embedding == null ? null : embedding.getOutput();
            if (vector == null || vector.length != dimension) {
                throw new DimensionMismatchException("返回向量维度 " + (vector == null ? -1 : vector.length)
                        + "，期望 " + dimension);
            }
            StringBuilder sb = new StringBuilder(vector.length * 12 + 2).append('[');
            for (int i = 0; i < vector.length; i++) {
                float value = vector[i];
                if (!Float.isFinite(value)) {
                    throw new DimensionMismatchException("返回向量含 NaN / Infinity");
                }
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(Float.toString(value));
            }
            literals.add(sb.append(']').toString());
        }
        return literals;
    }

    private static int usageTokens(EmbeddingResponse response) {
        if (response == null) {
            return 0;
        }
        // getMetadata() 在不同 Spring AI 版本返回 ResponseMetadata 或其协变子类型，
        // 统一按 Object 接收再做 instanceof，避免依赖具体签名
        Object raw = response.getMetadata();
        if (!(raw instanceof EmbeddingResponseMetadata metadata)
                || metadata.getUsage() == null || metadata.getUsage().getPromptTokens() == null) {
            return 0;
        }
        return Math.max(0, metadata.getUsage().getPromptTokens());
    }

    private static int elapsed(long begin) {
        return (int) (System.currentTimeMillis() - begin);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    // ==================== 调用日志（与 M1 治理日志同表、同字段口径） ====================

    private void writeLog(Resolved resolved, Long tenantId, String traceId, int status,
                          int latencyMs, int tokens, String circuitKey,
                          ModelErrorCategory category, String errorMsg) {
        try {
            AiModelCallLog row = new AiModelCallLog();
            row.setTraceId(traceId);
            row.setConversationId(0L);
            row.setSceneCode(SCENE_CODE);
            row.setProviderId(resolved.config.getProviderId());
            row.setModelConfigId(resolved.config.getId());
            row.setModelId(resolved.config.getModelId());
            row.setRequestTokens(tokens);
            row.setResponseTokens(0);
            row.setTotalTokens(tokens);
            // Embedding 计费以供应商账单为准；单价单位尚未在 ai_model_config 中统一约定，这里不臆造折算
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
            row.setCreator("schema-embedding");
            row.setUpdater("schema-embedding");
            LocalDateTime now = LocalDateTime.now();
            row.setCreateTime(now);
            row.setUpdateTime(now);
            row.setDeleted(0);
            // 私有表 INSERT 不会被 DefaultDBFieldHandler 自动填充，必须显式写 tenant_id
            row.setTenantId(tenantId);
            callLogService.save(row);
        } catch (Exception ex) {
            // 日志写失败不阻断向量构建，但必须留痕
            log.warn("[writeLog][Embedding 调用日志写入失败 tenantId={} traceId={}] {}",
                    tenantId, traceId, ex.toString());
        }
    }

    /** 维度不符属于配置错误，单独标记以免污染下游故障熔断计数 */
    private static final class DimensionMismatchException extends RuntimeException {

        DimensionMismatchException(String message) {
            super(message);
        }
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
