package cn.iocoder.boot.module.data_query.framework.tenant;

import cn.iocoder.yudao.framework.common.biz.system.tenant.TenantCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听租户创建（原「自动初始化 AI 配置骨架」）。
 *
 * <p>2026-11-26 起平台模板已移除，监听器调用的是空操作，不再为新租户写入任何配置；
 * 租户管理员需自行在管理端创建供应商、模型配置与场景绑定。监听器保留是为了维持
 * 租户创建事件链路不变，便于将来若改为其它初始化策略时直接接上。
 *
 * 依赖方向：事件类在 yudao-common，system 只负责发布，dataQuery 负责订阅，
 * system 不需要反向依赖 dataQuery。
 *
 * 兜底：任何异常必须在这里吞掉，不能阻断租户创建主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTenantInitListener {

    private final AiTenantInitializer aiTenantInitializer;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        if (event == null || event.getTenantId() == null) {
            return;
        }
        try {
            aiTenantInitializer.initDefaultConfig(event.getTenantId());
        } catch (Exception ex) {
            log.error("[onTenantCreated][租户({}) AI 配置初始化失败，请用管理端兜底接口补刷] {}",
                    event.getTenantId(), ex.getMessage(), ex);
        }
    }

}
