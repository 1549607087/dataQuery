package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 知识库 新增/修改 Request VO
 *
 * 知识库为租户私有实体，tenant_id 由 DB 租户插件自动注入，接口不接收 tenant_id。
 * embedding_model_code 由系统根据当前租户的 EMBEDDING 场景在上传/重建时写入，
 * 属于运行时指纹（provider.code:model_id:dimension），不开放给前端手填，避免与 chunk 实际值不一致。
 */
@Schema(description = "管理后台 - 知识库新增/修改 Request VO")
@Data
public class AiKnowledgeBaseSaveReqVO {

    @Schema(description = "编号，更新时必填；新增时不传", example = "1024")
    private Long id;

    @Schema(description = "知识库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "产品知识库")
    @NotEmpty(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过 128")
    private String name;

    @Schema(description = "业务编码，租户内唯一（部分唯一索引 uk_ai_knowledge_base_01）", requiredMode = Schema.RequiredMode.REQUIRED, example = "product_kb")
    @NotEmpty(message = "业务编码不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "业务编码只能包含字母、数字、下划线、短横线")
    @Size(max = 64, message = "业务编码长度不能超过 64")
    private String code;

    @Schema(description = "知识库描述", example = "产品相关资料与常见问题")
    @Size(max = 512, message = "知识库描述长度不能超过 512")
    private String description;

    @Schema(description = "状态：0 正常、1 停用；停用后该库不参与检索", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

}
