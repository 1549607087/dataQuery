package cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - 模型供应商新增/修改 Request VO
 *
 * 注意：apiKey / apiSecret 为「写」字段，接口不回显；
 * 修改时留空表示不覆盖已配置的密文。
 */
@Schema(description = "管理后台 - 模型供应商新增/修改 Request VO")
@Data
public class AiModelProviderSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "供应商名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "通义千问")
    @NotEmpty(message = "供应商名称不能为空")
    private String name;

    @Schema(description = "供应商编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen")
    @NotEmpty(message = "供应商编码不能为空")
    private String code;

    @Schema(description = "协议：OPENAI / OLLAMA / AZURE / CUSTOM", requiredMode = Schema.RequiredMode.REQUIRED, example = "OPENAI")
    @NotEmpty(message = "协议不能为空")
    private String protocol;

    @Schema(description = "API 基础地址", example = "https://dashscope.aliyuncs.com/compatible-mode/v1")
    private String baseUrl;

    @Schema(description = "API Key（留空表示不修改）")
    private String apiKey;

    @Schema(description = "备用凭证（留空表示不修改）")
    private String apiSecret;

    @Schema(description = "自定义请求头，JSON 字符串")
    private String headers;

    @Schema(description = "代理地址")
    private String proxyUrl;

    @Schema(description = "每日 Token 上限，0 表示不限")
    private Long dailyTokenLimit;

    @Schema(description = "月度预算（元），0 表示不限")
    private BigDecimal monthlyBudget;

    @Schema(description = "状态：0 启用、1 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
