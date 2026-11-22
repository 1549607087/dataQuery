package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库问答会话改名 Request VO
 */
@Schema(description = "管理后台 - 知识库问答会话改名 Request VO")
@Data
public class KnowledgeSessionRenameReqVO {

    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "会话编号不能为空")
    private Long sessionId;

    @Schema(description = "新标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100 字符")
    private String title;
}
