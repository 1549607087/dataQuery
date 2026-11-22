package cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台 - 模型供应商精简 Response VO（下拉框使用）
 */
@Schema(description = "管理后台 - 模型供应商精简 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModelProviderSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "供应商名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "通义千问")
    private String name;

    @Schema(description = "供应商编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen")
    private String code;

}
