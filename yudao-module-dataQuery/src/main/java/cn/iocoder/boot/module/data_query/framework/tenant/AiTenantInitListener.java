package cn.iocoder.boot.module.data_query.framework.tenant;

import cn.iocoder.yudao.framework.common.biz.system.tenant.TenantCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听租户创建，自动初始化该租户的 AI 模型配置骨架。
 *
 * 依赖方向：事件类在 yudao-common，system 只负责发布，dataQuery 负责订阅，
 * system 不需要反向依赖 dataQuery。
 *
 * 兜底：初始化失败必须在这里吞掉异常，不能阻断租户创建主流程；
 * 运维可通过 POST /admin-api/data-query/tenant-init/init?tenantId=xxx 手动补刷。
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
