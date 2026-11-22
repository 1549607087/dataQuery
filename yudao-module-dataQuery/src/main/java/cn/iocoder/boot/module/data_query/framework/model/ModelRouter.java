package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelGrayExperiment;
import cn.iocoder.boot.module.data_query.entity.AiModelRouteCandidate;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.service.AiModelConfigService;
import cn.iocoder.boot.module.data_query.service.AiModelGrayExperimentService;
import cn.iocoder.boot.module.data_query.service.AiModelRouteCandidateService;
import cn.iocoder.boot.module.data_query.service.AiModelSceneBindingService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型路由器
 *
 * 职责：场景 → 候选模型集 → 按策略排序 → 灰度稳定分桶 → 输出可执行的降级链。
 *
 * 关键约束：
 * 1) 每次调用都查库，不加本地缓存 —— 启停 / 灰度比例 / 候选权重的修改必须立即生效。
 *    若将来加缓存，TTL 必须 <= 10s 且配合变更广播失效，否则「停用」就是摆设。
 * 2) 灰度必须稳定分桶（按 TENANT / USER / SESSION 哈希），不能每次随机，
 *    否则同一用户两次提问命中不同模型，A/B 数据无法归因。
 * 3) 降级链跨供应商由 ModelGovernanceExecutor 在切换候选时校验，
 *    同供应商的另一个模型等于没降级。
 *
 * ⚠️ 已知约束（沿用上一轮排障结论）：TenantDatabaseInterceptor 会对所有表追加
 *    tenant_id = 当前租户，tenant_id = 0 的平台级配置查不出来。要支持「平台级模板 +
 *    租户覆盖」，需把相关表加入 yudao.tenant.ignore-tables 或 DO 加 @TenantIgnore，
 *    再由这里显式按 tenant_id IN (当前租户, 0) 过滤。当前未改框架配置。
 */
@Slf4j
@Component
public class ModelRouter {

    public static final int STRATEGY_FIXED = 1;
    public static final int STRATEGY_PRIORITY = 2;
    public static final int STRATEGY_WEIGHT = 3;
    public static final int STRATEGY_COST = 4;

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_GRAY = 2;

    @Resource
    private AiModelSceneBindingService aiModelSceneBindingService;
    @Resource
    private AiModelRouteCandidateService aiModelRouteCandidateService;
    @Resource
    private AiModelConfigService aiModelConfigService;
    @Resource
    private AiModelGrayExperimentService aiModelGrayExperimentService;

    /**
     * 为一次调用生成路由决策
     */
    public ModelRoutePlan route(ModelInvocation invocation) {
        if (!StringUtils.hasText(invocation.getSceneCode())) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR, "场景编码不能为空");
        }
        AiModelSceneBinding binding = findBinding(invocation.getSceneCode());
        int strategy = binding.getRouteStrategy() == null ? STRATEGY_FIXED : binding.getRouteStrategy();
        String bucketValue = resolveBucketValue(binding, invocation);

        // 1. 候选集：优先读 ai_model_route_candidate，查不到回落解析旧 fallback_model_ids
        List<AiModelRouteCandidate> candidateRows = aiModelRouteCandidateService.list(
                new LambdaQueryWrapperX<AiModelRouteCandidate>()
                        .eq(AiModelRouteCandidate::getBindingId, binding.getId())
                        .eq(AiModelRouteCandidate::getEnabled, 1)
                        .eq(AiModelRouteCandidate::getDeleted, 0)
                        .orderByAsc(AiModelRouteCandidate::getPriority)
                        .orderByAsc(AiModelRouteCandidate::getSort));
        boolean fromTable = !candidateRows.isEmpty();
        if (!fromTable) {
            candidateRows = fallbackFromLegacy(binding);
        }

        // 2. 关联模型配置，过滤停用 / 已删除，并保持候选顺序去重
        Map<Long, AiModelConfig> configMap = loadConfigs(candidateRows);
        Map<Long, AiModelRouteCandidate> rowMap = new LinkedHashMap<>();
        for (AiModelRouteCandidate row : candidateRows) {
            AiModelConfig config = configMap.get(row.getModelId());
            if (config != null) {
                rowMap.putIfAbsent(row.getModelId(), row);
            }
        }
        if (rowMap.isEmpty()) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "场景 " + invocation.getSceneCode() + " 没有可用的启用模型，请检查场景绑定与候选集");
        }

        // 3. 按路由策略排序
        List<AiModelConfig> candidates = sortByStrategy(strategy, rowMap, configMap, bucketValue,
                invocation.getSceneCode());

        // 4. 灰度：优先读「进行中」的实验，一次只灰度一个变量
        ModelRoutePlan plan = new ModelRoutePlan();
        plan.setBinding(binding);
        plan.setRouteStrategy(strategy);
        plan.setFromCandidateTable(fromTable);
        plan.setBucketValue(bucketValue);
        plan.setGray(Boolean.FALSE);
        plan.setGrayGroup("");
        plan.setGrayExperimentId(0L);
        plan.setGrayBucketKey(defaultIfBlank(binding.getGrayBucketKey(), "TENANT"));
        plan.setPromptIdOverride(0L);

        AiModelGrayExperiment experiment = findRunningExperiment(binding.getGrayExperimentId());
        int grayRatio;
        if (experiment != null) {
            grayRatio = experiment.getGrayRatio() == null ? 0 : experiment.getGrayRatio();
            plan.setGrayExperimentId(experiment.getId());
            plan.setGrayBucketKey(defaultIfBlank(experiment.getBucketKey(), plan.getGrayBucketKey()));
        } else {
            grayRatio = binding.getGrayRatio() == null ? 0 : binding.getGrayRatio();
        }

        if (grayRatio > 0) {
            boolean hit = Math.floorMod((bucketValue + "|" + invocation.getSceneCode()
                    + "|" + plan.getGrayExperimentId()).hashCode(), 100) < grayRatio;
            plan.setGray(Boolean.TRUE);
            plan.setGrayGroup(hit ? "B" : "A");
            if (experiment != null && "PROMPT".equalsIgnoreCase(experiment.getGrayObject())) {
                plan.setPromptIdOverride(hit ? experiment.getGrayPromptId() : experiment.getBaselinePromptId());
            } else if (experiment != null && "MODEL".equalsIgnoreCase(experiment.getGrayObject())) {
                Long target = hit ? experiment.getGrayModelId() : experiment.getBaselineModelId();
                promote(candidates, target);
            } else if (hit && candidates.size() > 1) {
                // 无实验时的探索性灰度：B 组使用次选模型。
                // 注意：没有明确对照组定义，无法做严格 A/B 归因，仅用于观察。
                AiModelConfig second = candidates.get(1);
                candidates.remove(1);
                candidates.add(0, second);
            }
        }

        // 5. 用户指定模型（对话页「选择模型」）：只能在候选集内提升为首选，
        //    放在灰度之后、截断之前 —— 用户显式选择优先于灰度分桶，同时仍保留完整降级链
        applyPreferred(candidates, invocation.getPreferredModelConfigId(), invocation.getSceneCode());

        // 6. 截断降级链，防止链过长放大延迟与成本
        int maxDepth = binding.getFallbackMaxDepth() == null || binding.getFallbackMaxDepth() < 0
                ? 2 : binding.getFallbackMaxDepth();
        int limit = Math.min(candidates.size(), 1 + maxDepth);
        plan.setCandidates(new ArrayList<>(candidates.subList(0, limit)));
        return plan;
    }

    /**
     * 场景当前可路由的模型（按策略与灰度排好序，首个即默认命中）。
     *
     * 对话页的「选择模型」下拉只展示这里返回的模型：范围与真实路由完全同源，
     * 避免用户选到一个不可路由的模型后才在问数时报错。
     * 场景未配置 / 无可用模型时抛出 ModelException，由调用方决定是否降级为空列表。
     */
    public List<AiModelConfig> listSceneCandidates(String sceneCode) {
        ModelInvocation invocation = new ModelInvocation();
        invocation.setSceneCode(sceneCode);
        invocation.setTenantId(TenantContextHolder.getTenantId());
        return route(invocation).getCandidates();
    }

    // ========== 内部方法 ==========

    /**
     * 把用户指定的模型提升到候选链首位（保持降级链其余顺序不变）。
     * 不在候选集时直接抛 CONFIG_ERROR：宁可明确报错，也不静默换成别的模型糊弄用户。
     */
    private void applyPreferred(List<AiModelConfig> candidates, Long preferredId, String sceneCode) {
        if (preferredId == null || preferredId <= 0) {
            return;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (preferredId.equals(candidates.get(i).getId())) {
                if (i > 0) {
                    AiModelConfig target = candidates.remove(i);
                    candidates.add(0, target);
                }
                return;
            }
        }
        throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                "所选模型不可用于场景 " + sceneCode + "（可能已停用、已删除或未加入该场景候选集）");
    }

    private AiModelSceneBinding findBinding(String sceneCode) {
        List<AiModelSceneBinding> list = aiModelSceneBindingService.list(
                new LambdaQueryWrapperX<AiModelSceneBinding>()
                        .eq(AiModelSceneBinding::getSceneCode, sceneCode)
                        .eq(AiModelSceneBinding::getEnabled, 1)
                        .eq(AiModelSceneBinding::getDeleted, 0)
                        .orderByAsc(AiModelSceneBinding::getSort));
        if (list.isEmpty()) {
            throw new ModelException(ModelErrorCategory.CONFIG_ERROR,
                    "场景 " + sceneCode + " 未配置启用的模型绑定");
        }
        return list.get(0);
    }

    /** 候选表为空时，兼容旧的 primary_model_id + fallback_model_ids 字符串 */
    private List<AiModelRouteCandidate> fallbackFromLegacy(AiModelSceneBinding binding) {
        List<AiModelRouteCandidate> rows = new ArrayList<>();
        int priority = 0;
        if (binding.getPrimaryModelId() != null && binding.getPrimaryModelId() > 0) {
            rows.add(legacyRow(binding.getId(), binding.getPrimaryModelId(), priority++));
        }
        if (StringUtils.hasText(binding.getFallbackModelIds())) {
            for (String part : binding.getFallbackModelIds().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && trimmed.matches("\\d+")) {
                    rows.add(legacyRow(binding.getId(), Long.valueOf(trimmed), priority++));
                }
            }
        }
        return rows;
    }

    private AiModelRouteCandidate legacyRow(Long bindingId, Long modelId, int priority) {
        AiModelRouteCandidate row = new AiModelRouteCandidate();
        row.setBindingId(bindingId);
        row.setModelId(modelId);
        row.setPriority(priority);
        row.setWeight(0);
        row.setEnabled(1);
        return row;
    }

    private Map<Long, AiModelConfig> loadConfigs(List<AiModelRouteCandidate> rows) {
        List<Long> ids = rows.stream().map(AiModelRouteCandidate::getModelId)
                .distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<AiModelConfig> configs = aiModelConfigService.listByIds(ids);
        Map<Long, AiModelConfig> map = new HashMap<>();
        Long currentTenantId = TenantContextHolder.getTenantId();
        for (AiModelConfig config : configs) {
            // 私有表虽由拦截器过滤，这里再校验一次归属：防止候选集里混入跨租户的 model_id
            if (currentTenantId != null && config.getTenantId() != null
                    && !currentTenantId.equals(config.getTenantId())) {
                continue;
            }
            if (config.getDeleted() != null && config.getDeleted() == 0
                    && config.getStatus() != null
                    && (config.getStatus() == STATUS_ENABLED || config.getStatus() == STATUS_GRAY)) {
                map.put(config.getId(), config);
            }
        }
        return map;
    }

    private List<AiModelConfig> sortByStrategy(int strategy,
                                               Map<Long, AiModelRouteCandidate> rowMap,
                                               Map<Long, AiModelConfig> configMap,
                                               String bucketValue, String sceneCode) {
        List<AiModelConfig> result = new ArrayList<>();
        switch (strategy) {
            case STRATEGY_COST -> {
                result.addAll(rowMap.keySet().stream().map(configMap::get)
                        .sorted(Comparator.comparing(this::unitCost)).collect(Collectors.toList()));
            }
            case STRATEGY_WEIGHT -> {
                // 权重用稳定哈希选择首选，其余仍按 priority 组成降级链，保证同一用户稳定命中
                List<Long> ids = new ArrayList<>(rowMap.keySet());
                int totalWeight = ids.stream().mapToInt(id -> {
                    Integer w = rowMap.get(id).getWeight();
                    return w == null || w < 0 ? 0 : w;
                }).sum();
                if (totalWeight > 0) {
                    int target = Math.floorMod((bucketValue + "|" + sceneCode).hashCode(), totalWeight);
                    int acc = 0;
                    Long chosen = null;
                    for (Long id : ids) {
                        Integer w = rowMap.get(id).getWeight();
                        acc += (w == null || w < 0 ? 0 : w);
                        if (target < acc) {
                            chosen = id;
                            break;
                        }
                    }
                    if (chosen != null) {
                        result.add(configMap.get(chosen));
                        for (Long id : ids) {
                            if (!id.equals(chosen)) {
                                result.add(configMap.get(id));
                            }
                        }
                        return result;
                    }
                }
                result.addAll(ids.stream().map(configMap::get).collect(Collectors.toList()));
            }
            case STRATEGY_PRIORITY -> {
                result.addAll(rowMap.keySet().stream().map(configMap::get)
                        .sorted(Comparator.comparingInt(c -> priorityOf(rowMap, c.getId())))
                        .collect(Collectors.toList()));
            }
            default -> {
                // 1 固定：候选表已按 priority 升序返回，primary 通常 priority 最小，保持原顺序
                result.addAll(rowMap.keySet().stream().map(configMap::get).collect(Collectors.toList()));
            }
        }
        return result;
    }

    private int priorityOf(Map<Long, AiModelRouteCandidate> rowMap, Long modelConfigId) {
        AiModelRouteCandidate row = rowMap.get(modelConfigId);
        return row == null || row.getPriority() == null ? Integer.MAX_VALUE : row.getPriority();
    }

    private BigDecimal unitCost(AiModelConfig config) {
        BigDecimal in = config.getInputPrice() == null ? BigDecimal.ZERO : config.getInputPrice();
        BigDecimal out = config.getOutputPrice() == null ? BigDecimal.ZERO : config.getOutputPrice();
        return in.add(out);
    }

    private void promote(List<AiModelConfig> candidates, Long targetId) {
        if (targetId == null || targetId <= 0) {
            return;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (targetId.equals(candidates.get(i).getId())) {
                AiModelConfig target = candidates.remove(i);
                candidates.add(0, target);
                return;
            }
        }
    }

    private AiModelGrayExperiment findRunningExperiment(Long experimentId) {
        if (experimentId == null || experimentId <= 0) {
            return null;
        }
        AiModelGrayExperiment experiment = aiModelGrayExperimentService.getById(experimentId);
        if (experiment == null || experiment.getDeleted() == null || experiment.getDeleted() != 0
                || experiment.getStatus() == null || experiment.getStatus() != 1) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (experiment.getStartTime() != null && now.isBefore(experiment.getStartTime())) {
            return null;
        }
        if (experiment.getEndTime() != null && now.isAfter(experiment.getEndTime())) {
            return null;
        }
        // 一次实验只允许一个变量，避免模型与提示词双灰度污染归因
        if (!Arrays.asList("MODEL", "PROMPT").contains(String.valueOf(experiment.getGrayObject()).toUpperCase())) {
            return null;
        }
        return experiment;
    }

    private String resolveBucketValue(AiModelSceneBinding binding, ModelInvocation invocation) {
        String dimension = defaultIfBlank(binding.getGrayBucketKey(), "TENANT");
        return switch (dimension.toUpperCase()) {
            case "USER" -> "user:" + invocation.getUserId();
            case "SESSION" -> "session:" + (StringUtils.hasText(invocation.getSessionId())
                    ? invocation.getSessionId() : String.valueOf(invocation.getConversationId()));
            default -> "tenant:" + invocation.getTenantId();
        };
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

}
