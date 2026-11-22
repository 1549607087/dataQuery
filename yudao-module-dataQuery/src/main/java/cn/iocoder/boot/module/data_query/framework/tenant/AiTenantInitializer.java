package cn.iocoder.boot.module.data_query.framework.tenant;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelProvider;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.service.AiModelConfigService;
import cn.iocoder.boot.module.data_query.service.AiModelProviderService;
import cn.iocoder.boot.module.data_query.service.AiModelSceneBindingService;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 租户 AI 配置初始化器：把 tenant_id = 0 的平台模板复制成目标租户的一套私有配置。
 *
 * 为什么必须用 Java 而不是纯 SQL：
 * 1. 主键是应用层雪花 ID（IdType.ASSIGN_ID），脚本里写死的小整数只是模板，直接 INSERT 会与真实数据冲突；
 * 2. provider_id / primary_model_id / fallback_model_ids 是跨表引用，复制时必须做新旧 ID 重映射。
 *
 * 幂等：逐条按业务键（provider.code / config.model_id / binding.scene_code）查重，
 * 已存在则跳过并复用其 ID 继续映射。中途失败后重跑可补齐剩余记录，不会产生重复数据，
 * 因此不依赖跨数据源事务（createTenant 用的是 @DSTransactional）。
 *
 * 密钥：模板的 api_key / api_secret 一律置 NULL，由租户管理员在管理端录入自己的凭证，平台不代管。
 * 数据源侧不在此初始化：数据源连接与元数据由各租户自行创建和同步，平台不共享。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTenantInitializer {

    private static final Long PLATFORM_TEMPLATE_TENANT_ID = 0L;
    private static final String INIT_OPERATOR = "tenant-init";

    private final AiModelProviderService providerService;
    private final AiModelConfigService configService;
    private final AiModelSceneBindingService bindingService;

    public void initDefaultConfig(Long tenantId) {
        Assert.notNull(tenantId, "tenantId 不能为空");

        // 1. 忽略租户读平台模板（拦截器默认会追加 tenant_id = 当前租户，必须显式跳过）
        List<AiModelProvider> providerTemplates = readTemplates((Callable<List<AiModelProvider>>) () ->
                providerService.list(new LambdaQueryWrapperX<AiModelProvider>()
                        .eq(AiModelProvider::getTenantId, PLATFORM_TEMPLATE_TENANT_ID)
                        .eq(AiModelProvider::getDeleted, 0)));
        List<AiModelConfig> configTemplates = readTemplates((Callable<List<AiModelConfig>>) () ->
                configService.list(new LambdaQueryWrapperX<AiModelConfig>()
                        .eq(AiModelConfig::getTenantId, PLATFORM_TEMPLATE_TENANT_ID)
                        .eq(AiModelConfig::getDeleted, 0)));
        List<AiModelSceneBinding> bindingTemplates = readTemplates((Callable<List<AiModelSceneBinding>>) () ->
                bindingService.list(new LambdaQueryWrapperX<AiModelSceneBinding>()
                        .eq(AiModelSceneBinding::getTenantId, PLATFORM_TEMPLATE_TENANT_ID)
                        .eq(AiModelSceneBinding::getDeleted, 0)));

        if (providerTemplates.isEmpty()) {
            log.warn("[initDefaultConfig][租户({}) 未初始化：平台模板 ai_model_provider 为空]", tenantId);
            return;
        }

        // 2. 在目标租户上下文内写入。显式 setTenantId：实体是普通 POJO，框架不会自动填充
        TenantUtils.execute(tenantId, () -> {
            Map<Long, Long> providerIdMap = new HashMap<>();
            for (AiModelProvider template : providerTemplates) {
                Long existingId = findProviderIdByCode(template.getCode());
                if (existingId != null) {
                    providerIdMap.put(template.getId(), existingId);
                    continue;
                }
                AiModelProvider copy = BeanUtils.toBean(template, AiModelProvider.class);
                fillCommonProvider(copy, tenantId);
                copy.setApiKey(null);
                copy.setApiSecret(null);
                providerService.save(copy);
                providerIdMap.put(template.getId(), copy.getId());
            }

            Map<Long, Long> configIdMap = new HashMap<>();
            for (AiModelConfig template : configTemplates) {
                Long existingId = findConfigIdByModelId(template.getModelId());
                if (existingId != null) {
                    configIdMap.put(template.getId(), existingId);
                    continue;
                }
                AiModelConfig copy = BeanUtils.toBean(template, AiModelConfig.class);
                fillCommonConfig(copy, tenantId);
                copy.setProviderId(providerIdMap.getOrDefault(template.getProviderId(), template.getProviderId()));
                configService.save(copy);
                configIdMap.put(template.getId(), copy.getId());
            }

            for (AiModelSceneBinding template : bindingTemplates) {
                if (findBindingIdBySceneCode(template.getSceneCode()) != null) {
                    continue;
                }
                AiModelSceneBinding copy = BeanUtils.toBean(template, AiModelSceneBinding.class);
                fillCommonBinding(copy, tenantId);
                copy.setPrimaryModelId(configIdMap.getOrDefault(template.getPrimaryModelId(), template.getPrimaryModelId()));
                copy.setFallbackModelIds(remapFallback(template.getFallbackModelIds(), configIdMap));
                bindingService.save(copy);
            }
            log.info("[initDefaultConfig][租户({}) 初始化完成 provider={} config={} binding={}]",
                    tenantId, providerIdMap.size(), configIdMap.size(), bindingTemplates.size());
        });
    }

    /** 查询指定租户的三类配置数量，供管理端兜底接口展示初始化状态 */
    public long[] countConfig(Long tenantId) {
        return TenantUtils.execute(tenantId, (Callable<long[]>) () -> new long[]{
                providerService.count(new LambdaQueryWrapperX<AiModelProvider>()
                        .eq(AiModelProvider::getDeleted, 0)),
                configService.count(new LambdaQueryWrapperX<AiModelConfig>()
                        .eq(AiModelConfig::getDeleted, 0)),
                bindingService.count(new LambdaQueryWrapperX<AiModelSceneBinding>()
                        .eq(AiModelSceneBinding::getDeleted, 0))
        });
    }

    private <V> V readTemplates(Callable<V> callable) {
        return TenantUtils.executeIgnore(callable);
    }

    private Long findProviderIdByCode(String code) {
        List<AiModelProvider> list = providerService.list(new LambdaQueryWrapperX<AiModelProvider>()
                .eq(AiModelProvider::getCode, code)
                .eq(AiModelProvider::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private Long findConfigIdByModelId(String modelId) {
        List<AiModelConfig> list = configService.list(new LambdaQueryWrapperX<AiModelConfig>()
                .eq(AiModelConfig::getModelId, modelId)
                .eq(AiModelConfig::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private Long findBindingIdBySceneCode(String sceneCode) {
        List<AiModelSceneBinding> list = bindingService.list(new LambdaQueryWrapperX<AiModelSceneBinding>()
                .eq(AiModelSceneBinding::getSceneCode, sceneCode)
                .eq(AiModelSceneBinding::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /** fallback_model_ids 是模型 ID 逗号分隔串，逐项按新旧映射重写 */
    private String remapFallback(String fallbackModelIds, Map<Long, Long> configIdMap) {
        if (!StringUtils.hasText(fallbackModelIds)) {
            return fallbackModelIds;
        }
        List<String> mapped = new ArrayList<>();
        for (String part : fallbackModelIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                Long oldId = Long.valueOf(trimmed);
                mapped.add(String.valueOf(configIdMap.getOrDefault(oldId, oldId)));
            } catch (NumberFormatException ex) {
                log.warn("[remapFallback][无法解析的模型 ID({})，原样保留]", trimmed);
                mapped.add(trimmed);
            }
        }
        return String.join(",", mapped);
    }

    private void fillCommonProvider(AiModelProvider entity, Long tenantId) {
        entity.setId(null);
        entity.setTenantId(tenantId);
        entity.setCreator(INIT_OPERATOR);
        entity.setUpdater(INIT_OPERATOR);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
    }

    private void fillCommonConfig(AiModelConfig entity, Long tenantId) {
        entity.setId(null);
        entity.setTenantId(tenantId);
        entity.setCreator(INIT_OPERATOR);
        entity.setUpdater(INIT_OPERATOR);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
    }

    private void fillCommonBinding(AiModelSceneBinding entity, Long tenantId) {
        entity.setId(null);
        entity.setTenantId(tenantId);
        entity.setCreator(INIT_OPERATOR);
        entity.setUpdater(INIT_OPERATOR);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
    }

}
