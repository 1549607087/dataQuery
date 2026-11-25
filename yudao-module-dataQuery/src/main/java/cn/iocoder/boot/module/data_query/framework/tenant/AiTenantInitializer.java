package cn.iocoder.boot.module.data_query.framework.tenant;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelProvider;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.service.AiModelConfigService;
import cn.iocoder.boot.module.data_query.service.AiModelProviderService;
import cn.iocoder.boot.module.data_query.service.AiModelSceneBindingService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.concurrent.Callable;

/**
 * 租户 AI 配置状态查询（原「平台模板复制初始化器」）。
 *
 * <p><b>2026-11-26 变更</b>：tenant_id = 0 的平台模板（provider / config /
 * scene_binding / route_candidate）已整体移除。此前新租户开通时由本类把模板复制成
 * 租户私有配置，并做雪花 ID 重生成与跨表 ID 重映射；模板删除后该机制不再成立，
 * 新租户的供应商、模型配置与场景绑定一律由租户管理员在管理端自行创建。
 *
 * <p>{@link #initDefaultConfig(Long)} 保留为空操作，只为不破坏两个既有调用方的契约：
 * ① 新建租户事件监听器 {@code AiTenantInitListener}；② 运维兜底接口
 * {@code AiTenantInitController#init}。调用后系统不做任何写入，日志明确告知需人工配置。
 *
 * <p>{@link #countConfig(Long)} 继续供 {@code /tenant-init/status} 展示当前配置数量，
 * 其中 initialized 的语义从「模板已复制」变为「租户已自行配齐三类配置」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTenantInitializer {

    private final AiModelProviderService providerService;
    private final AiModelConfigService configService;
    private final AiModelSceneBindingService bindingService;

    /**
     * 兼容旧调用的空操作：平台模板已移除，不再自动写入任何租户配置。
     *
     * @param tenantId 目标租户编号
     */
    public void initDefaultConfig(Long tenantId) {
        Assert.notNull(tenantId, "tenantId 不能为空");
        log.info("[initDefaultConfig][租户({}) 平台模板已移除，跳过自动初始化；"
                + "请在管理端自行创建模型供应商、模型配置与场景绑定]", tenantId);
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

}
