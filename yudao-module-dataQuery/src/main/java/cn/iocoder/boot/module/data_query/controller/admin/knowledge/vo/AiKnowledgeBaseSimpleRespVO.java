package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 知识库精简 Response VO（供问答/下拉选择使用）
 */
@Schema(description = "管理后台 - 知识库精简 Response VO")
@Data
public class AiKnowledgeBaseSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "知识库名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "业务编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "状态：0 正常、1 停用")
    private Integer status;

}
