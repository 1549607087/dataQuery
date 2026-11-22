package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.governance.ModelInvokeReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.governance.ModelRoutePreviewRespVO;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.framework.model.ModelCircuitBreaker;
import cn.iocoder.boot.module.data_query.framework.model.ModelClient;
import cn.iocoder.boot.module.data_query.framework.model.ModelGovernanceExecutor;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocation;
import cn.iocoder.boot.module.data_query.framework.model.ModelInvocationResult;
import cn.iocoder.boot.module.data_query.framework.model.ModelRoutePlan;
import cn.iocoder.boot.module.data_query.framework.model.ModelRouter;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 模型治理链路（路由预览 / 联调 / 人工止血）
 *
 * 存在的意义：此前 M1 的路由、灰度、限流、熔断参数只有字段、没有运行时消费方，
 * 管理端改了配置行为却不变。本 Controller 提供两个可验证入口：
 * - /route-preview 不调用模型，直接返回「这次请求会怎么走」，验证启停 / 灰度 / 权重即时生效；
 * - /invoke 走完整治理链路，结果里的 echo 标记提示当前是否为占位客户端。
 */
@Tag(name = "管理后台 - 模型治理链路")
@RestController
@RequestMapping("/data-query/ai-model-governance")
@Validated
public class ModelGovernanceController {

    @Resource
    private ModelRouter modelRouter;
    @Resource
    private ModelGovernanceExecutor modelGovernanceExecutor;
    @Resource
    private ModelCircuitBreaker modelCircuitBreaker;
    @Resource
    private ModelClient modelClient;

    @GetMapping("/route-preview")
    @Operation(summary = "路由预览：查看某场景当前会选中哪些模型（不发起模型调用）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-governance:query')")
    public CommonResult<ModelRoutePreviewRespVO> routePreview(
            @RequestParam("sceneCode") @Parameter(description = "场景编码", required = true) String sceneCode,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        ModelInvocation invocation = buildInvocation(sceneCode, null, userId, tenantId,
                conversationId, sessionId, null);
        ModelRoutePlan plan = modelRouter.route(invocation);
        return success(toPreviewVO(sceneCode, plan));
    }

    @PostMapping("/invoke")
    @Operation(summary = "联调：走完整治理链路执行一次模型调用")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-governance:create')")
    public CommonResult<ModelInvocationResult> invoke(@Valid @RequestBody ModelInvokeReqVO reqVO) {
        ModelInvocation invocation = buildInvocation(reqVO.getSceneCode(), reqVO.getPrompt(),
                reqVO.getUserId(), reqVO.getTenantId(), reqVO.getConversationId(),
                reqVO.getSessionId(), reqVO.getTraceId());
        return success(modelGovernanceExecutor.execute(invocation, modelClient));
    }

    @PutMapping("/reset-circuit")
    @Operation(summary = "人工止血：重置指定熔断维度为 CLOSED 并清除限流 key")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-governance:update')")
    public CommonResult<Boolean> resetCircuit(
            @RequestParam("circuitKey") @Parameter(description = "熔断维度键，如 1:2", required = true) String circuitKey) {
        modelCircuitBreaker.reset(circuitKey);
        return success(true);
    }

    private ModelInvocation buildInvocation(String sceneCode, String prompt, Long userId,
                                            Long tenantId, Long conversationId,
                                            String sessionId, String traceId) {
        ModelInvocation invocation = new ModelInvocation();
        invocation.setSceneCode(sceneCode);
        invocation.setPrompt(prompt);
        invocation.setTraceId(traceId);
        invocation.setConversationId(conversationId);
        invocation.setUserId(userId == null ? SecurityFrameworkUtils.getLoginUserId() : userId);
        // 优先取真实租户上下文，避免所有请求都被硬编码成 1 导致分桶与日志归属错误；
        // 无上下文时（内部调用）退化为框架默认租户 1，分桶只要求稳定
        Long currentTenantId = TenantContextHolder.getTenantId();
        invocation.setTenantId(tenantId == null
                ? (currentTenantId == null ? 1L : currentTenantId)
                : tenantId);
        invocation.setSessionId(sessionId);
        return invocation;
    }

    private ModelRoutePreviewRespVO toPreviewVO(String sceneCode, ModelRoutePlan plan) {
        ModelRoutePreviewRespVO vo = new ModelRoutePreviewRespVO();
        vo.setSceneCode(sceneCode);
        vo.setRouteStrategy(plan.getRouteStrategy());
        vo.setFromCandidateTable(plan.getFromCandidateTable());
        vo.setGray(plan.getGray());
        vo.setGrayGroup(plan.getGrayGroup());
        vo.setGrayExperimentId(plan.getGrayExperimentId());
        vo.setGrayBucketKey(plan.getGrayBucketKey());
        vo.setBucketValue(plan.getBucketValue());
        vo.setPromptIdOverride(plan.getPromptIdOverride());

        List<ModelRoutePreviewRespVO.CandidateVO> candidates = new ArrayList<>();
        for (AiModelConfig config : plan.getCandidates()) {
            ModelRoutePreviewRespVO.CandidateVO item = new ModelRoutePreviewRespVO.CandidateVO();
            item.setModelConfigId(config.getId());
            item.setModelId(config.getModelId());
            item.setName(config.getName());
            item.setProviderId(config.getProviderId());
            item.setStatus(config.getStatus());
            item.setInputPrice(config.getInputPrice());
            item.setOutputPrice(config.getOutputPrice());
            item.setCircuitEnabled(config.getCircuitEnabled());
            item.setCircuitKey(ModelCircuitBreaker.circuitKey(config));
            candidates.add(item);
        }
        vo.setCandidates(candidates);

        AiModelSceneBinding binding = plan.getBinding();
        AiModelConfig primary = candidates.isEmpty() ? null : plan.getCandidates().get(0);
        ModelRoutePreviewRespVO.EffectiveVO effective = new ModelRoutePreviewRespVO.EffectiveVO();
        if (primary != null) {
            effective.setTimeoutMs(resolve(binding.getTimeoutMsOverride(), primary.getTimeoutMs(), 8000));
            effective.setQpsLimit(resolve(binding.getQpsLimitOverride(), primary.getQpsLimit(), 0));
            effective.setConcurrentLimit(resolve(binding.getConcurrentLimitOverride(), primary.getConcurrentLimit(), 0));
            effective.setRetryCount(resolve(binding.getRetryCountOverride(), primary.getRetryCount(), 0));
            effective.setRetryableErrors(primary.getRetryableErrors());
            effective.setRetryBaseBackoffMs(primary.getRetryBaseBackoffMs());
            effective.setRetryMaxBackoffMs(primary.getRetryMaxBackoffMs());
            effective.setRetryJitterRatio(primary.getRetryJitterRatio());
        }
        effective.setFallbackMaxDepth(binding.getFallbackMaxDepth());
        effective.setFallbackCrossProvider(binding.getFallbackCrossProvider());
        vo.setEffective(effective);
        return vo;
    }

    private Integer resolve(Integer override, Integer modelValue, int defaultValue) {
        return override != null ? override : (modelValue == null ? defaultValue : modelValue);
    }

}
