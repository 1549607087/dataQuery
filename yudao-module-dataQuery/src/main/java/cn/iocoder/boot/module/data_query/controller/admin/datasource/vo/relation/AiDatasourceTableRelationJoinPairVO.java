package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理后台 - 表关系 / Join 路径的字段对
 *
 * 前端只传结构化的字段名，由服务端序列化为 join_pairs JSON 后入库；
 * 字段名规则与 SchemaRecallServiceImpl.SQL_IDENTIFIER 保持一致，避免拼出的 SQL 被注入。
 */
@Schema(description = "表关系 / Join 路径 - 字段对")
@Data
public class AiDatasourceTableRelationJoinPairVO {

    @Schema(description = "左表字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "customer_id")
    @NotBlank(message = "左表字段名不能为空")
    @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_$]*", message = "左表字段名只能包含字母、数字、下划线和 $，且不能以数字开头")
    private String left;

    @Schema(description = "右表字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "id")
    @NotBlank(message = "右表字段名不能为空")
    @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_$]*", message = "右表字段名只能包含字母、数字、下划线和 $，且不能以数字开头")
    private String right;

}
