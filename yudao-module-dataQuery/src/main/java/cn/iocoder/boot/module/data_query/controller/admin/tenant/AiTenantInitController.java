package cn.iocoder.boot.module.data_query.controller.admin.tenant;

import cn.iocoder.boot.module.data_query.controller.admin.tenant.vo.AiTenantInitStatusRespVO;
import cn.iocoder.boot.module.data_query.framework.tenant.AiTenantInitializer;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 租户 AI 配置初始化（兜底 / 存量补刷）
 *
 * 正常路径：新建租户时 system 发布 TenantCreatedEvent，AiTenantInitListener 自动初始化。
 * 本接口用于：存量租户补刷、自动初始化失败后的手动重试。
 *
 * 权限边界：该接口只创建「密钥为空的配置骨架」，不读取也不返回任何租户的凭证或业务数据，
 * 因此允许平台运维跨租户补刷。菜单 data-query:tenant-init:create / :query 应按套餐
 * 只分配给平台管理员，不要下放给普通租户管理员（见 docs/tenant-isolation-design.md 第 7、11 章）。
 */
@Tag(name = "管理后台 - 租户 AI 配置初始化")
@RestController
@RequestMapping("/data-query/tenant-init")
@Validated
public class AiTenantInitController {

    @Resource
    private AiTenantInitializer aiTenantInitializer;

    @PostMapping("/init")
    @Operation(summary = "初始化指定租户的 AI 模型配置（幂等）")
    @Parameter(name = "tenantId", description = "租户编号；不传则取当前租户", required = false)
    @PreAuthorize("@ss.hasPermission('data-query:tenant-init:create')")
    public CommonResult<Boolean> init(@RequestParam(value = "tenantId", required = false) Long tenantId) {
        aiTenantInitializer.initDefaultConfig(resolveTenantId(tenantId));
        return success(true);
    }

    @GetMapping("/status")
    @Operation(summary = "查询指定租户的 AI 配置初始化状态")
    @Parameter(name = "tenantId", description = "租户编号；不传则取当前租户", required = false)
    @PreAuthorize("@ss.hasPermission('data-query:tenant-init:query')")
    public CommonResult<AiTenantInitStatusRespVO> status(
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        Long target = resolveTenantId(tenantId);
        long[] counts = aiTenantInitializer.countConfig(target);
        AiTenantInitStatusRespVO vo = new AiTenantInitStatusRespVO();
        vo.setTenantId(target);
        vo.setProviderCount(counts[0]);
        vo.setConfigCount(counts[1]);
        vo.setBindingCount(counts[2]);
        vo.setInitialized(counts[0] > 0 && counts[1] > 0 && counts[2] > 0);
        return success(vo);
    }

    private Long resolveTenantId(Long requested) {
        if (requested != null) {
            return requested;
        }
        Long current = TenantContextHolder.getTenantId();
        return current == null ? 0L : current;
    }

}
