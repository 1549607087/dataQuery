package cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 模型供应商 Response VO
 *
 * 出于安全考虑，apiKey / apiSecret 不出现在响应中。
 */
@Schema(description = "管理后台 - 模型供应商 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelProviderRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "供应商名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("供应商名称")
    private String name;

    @Schema(description = "供应商编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("供应商编码")
    private String code;

    @Schema(description = "协议")
    @ExcelProperty("协议")
    private String protocol;

    @Schema(description = "API 基础地址")
    @ExcelProperty("API 基础地址")
    private String baseUrl;

    @Schema(description = "自定义请求头，JSON 字符串")
    private String headers;

    @Schema(description = "代理地址")
    private String proxyUrl;

    @Schema(description = "每日 Token 上限，0 表示不限")
    @ExcelProperty("每日 Token 上限")
    private Long dailyTokenLimit;

    @Schema(description = "月度预算（元），0 表示不限")
    @ExcelProperty("月度预算")
    private BigDecimal monthlyBudget;

    @Schema(description = "状态：0 启用、1 停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    /** 仅表示是否已录入凭证，绝不返回任何密文或明文片段 */
    @Schema(description = "是否已配置凭证", example = "true")
    @ExcelProperty("已配置凭证")
    private Boolean configured;

}
