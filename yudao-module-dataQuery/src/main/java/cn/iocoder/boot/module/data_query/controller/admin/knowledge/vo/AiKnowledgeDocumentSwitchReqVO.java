package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 知识库文档 - 切换当前生效版本 Request VO
 *
 * <p>只切 is_latest 标记，不重新解析、不动文件与切片，零 Embedding 成本。
 * 用 docKey + version 定位目标版本；同 base 下每个 docKey 至多一个生效版本。
 */
@Schema(description = "管理后台 - 知识库文档切换生效版本 Request VO")
@Data
public class AiKnowledgeDocumentSwitchReqVO {

    @Schema(description = "知识库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "知识库编号不能为空")
    private Long baseId;

    @Schema(description = "逻辑文档标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文档标识不能为空")
    private String docKey;

    @Schema(description = "目标版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "目标版本号不能为空")
    private Integer version;
}
