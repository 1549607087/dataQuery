package cn.iocoder.yudao.framework.common.biz.system.tenant;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 租户创建事件。
 *
 * 框架原先没有租户创建回调：TenantServiceImpl#createTenant 只建 tenant/role/user，
 * TenantInfoHandler / TenantMenuHandler 是「以某租户上下文执行一段逻辑」的工具方法，不是回调。
 * 本事件由 system 模块在创建租户末尾发布，业务模块（如 dataQuery）通过 @EventListener 订阅，
 * 从而避免 system 反向依赖业务模块。
 *
 * 刻意不继承 org.springframework.context.ApplicationEvent：yudao-common 只依赖 spring-core，
 * 不依赖 spring-context。Spring 4.2+ 的 ApplicationEventPublisher#publishEvent(Object) 支持
 * 任意 POJO 作为事件，功能等价。
 *
 * 监听方必须自行兜底异常，不能让初始化失败阻断租户创建主流程。
 */
@Data
@NoArgsConstructor
public class TenantCreatedEvent {

    /** 新建的租户编号 */
    private Long tenantId;

    /** 事件发生时间，便于日志排查与幂等判断 */
    private LocalDateTime createTime;

    public TenantCreatedEvent(Long tenantId) {
        this.tenantId = tenantId;
        this.createTime = LocalDateTime.now();
    }

}
