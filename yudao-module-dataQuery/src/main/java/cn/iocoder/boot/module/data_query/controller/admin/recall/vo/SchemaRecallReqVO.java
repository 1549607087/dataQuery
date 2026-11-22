package cn.iocoder.boot.module.data_query.controller.admin.recall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - S3 Schema 召回 Request VO
 *
 * 刻意不接受 tenantId：租户编号只由服务端从鉴权上下文取，前端传值一律忽略，
 * 避免「换一个 header 就能查别人向量」的越权。
 */
@Schema(description = "管理后台 - S3 Schema 召回 Request VO")
@Data
public class SchemaRecallReqVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源编号不能为空")
    private Long datasourceId;

    @Schema(description = "用户问题（S2 归一化前的原文亦可，服务端做轻量归一化）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "召回问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "期望返回的表数量，不传用配置默认值 6")
    @Min(value = 1, message = "Top-K 最小为 1")
    @Max(value = 20, message = "Top-K 最大为 20")
    private Integer topK;

}
