package cn.iocoder.boot.module.data_query.controller.admin.sqlguard.vo;

import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - S6 仅校验 / S7 只读执行 Request VO（P4）。
 *
 * 刻意不接受 tenantId、连接串、账号、maxRows、sqlRules：这些全部由服务端从
 * 鉴权上下文与 ai_datasource 现取，前端伪造一律无效。
 *
 * schemaContext 允许前端传（P5 会直接复用 S3 召回结果），但服务端只取其中的
 * tableIds / columnIds，并且必须与 PG 可见可查元数据取交集；伪造更大的白名单
 * 只能收窄，不能放行任何一张本租户不可见的表。不传时必须给 question 由服务端自行召回。
 */
@Schema(description = "管理后台 - SQL 安全闸门 Request VO")
@Data
public class SqlGuardReqVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源编号不能为空")
    private Long datasourceId;

    @Schema(description = "模型原始文本或手工编辑的 SQL，支持 markdown ```sql 代码块",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "SQL 文本不能为空")
    @Size(max = 20000, message = "SQL 文本长度不能超过 20000 字符")
    private String sql;

    @Schema(description = "用户问题；schemaContext 为空时由服务端据此自动执行 S3 召回")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "S3 召回结果（可选）；为空时必须提供 question")
    private SchemaContext schemaContext;

}
