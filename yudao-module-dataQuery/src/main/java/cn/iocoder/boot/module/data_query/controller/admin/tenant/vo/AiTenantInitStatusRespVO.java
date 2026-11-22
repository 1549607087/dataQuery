package cn.iocoder.boot.module.data_query.controller.admin.tenant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 租户 AI 配置初始化状态 Response VO
 */
@Schema(description = "管理后台 - 租户 AI 配置初始化状态 Response VO")
@Data
public class AiTenantInitStatusRespVO {

    @Schema(description = "租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long tenantId;

    @Schema(description = "模型供应商数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long providerCount;

    @Schema(description = "模型配置数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long configCount;

    @Schema(description = "场景绑定数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long bindingCount;

    @Schema(description = "是否已完成初始化", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean initialized;

}
