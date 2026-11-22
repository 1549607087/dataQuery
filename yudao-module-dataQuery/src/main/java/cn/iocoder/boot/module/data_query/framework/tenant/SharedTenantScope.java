package cn.iocoder.boot.module.data_query.framework.tenant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 平台共享数据的租户范围工具。
 *
 * 背景：{@code TenantLineHandler#getTenantId()} 只能返回单个值，框架拦截器结构上无法为指定表
 * 生成 {@code tenant_id IN (当前租户, 0)}。因此共享表只能整体放行（ignore-tables / @TenantIgnore），
 * 再由业务层显式限定范围。放行不等于放开：查询限定为「当前租户 + 平台」，写入只允许当前租户。
 *
 * 适用范围：仅提示词 4 张表（ai_prompt_scene / ai_prompt / ai_prompt_version / ai_prompt_variable）。
 * 数据源连接、元数据、同步日志以及模型/治理相关表全部租户私有，不使用本类。
 */
public final class SharedTenantScope {

    /** 平台级数据（模板）的租户编号 */
    public static final Long PLATFORM_TENANT_ID = 0L;

    private SharedTenantScope() {
    }

    /** 当前租户；无租户上下文（如内部定时任务）时按平台处理 */
    public static Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? PLATFORM_TENANT_ID : tenantId;
    }

    /** 可见范围：正常租户为 [当前租户, 0]；平台上下文为 [0] */
    public static Long[] visibleTenantIds() {
        Long current = currentTenantId();
        if (PLATFORM_TENANT_ID.equals(current)) {
            return new Long[]{PLATFORM_TENANT_ID};
        }
        return new Long[]{current, PLATFORM_TENANT_ID};
    }

    /** 供 inSql 使用的可见租户列表，如 "1,0"；值全部来自 Long，无 SQL 注入风险 */
    public static String visibleTenantIdSql() {
        return Arrays.stream(visibleTenantIds()).map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 供 inSql 使用的可写租户：只允许当前租户，平台模板在租户侧只读 */
    public static String writableTenantIdSql() {
        return String.valueOf(currentTenantId());
    }

    /** 目标记录的归属是否允许当前上下文写入 */
    public static boolean isWritable(Long ownerTenantId) {
        Long owner = ownerTenantId == null ? PLATFORM_TENANT_ID : ownerTenantId;
        return currentTenantId().equals(owner);
    }

}
