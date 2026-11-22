package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档上传 Request VO
 *
 * 文件本身与归属知识库一起提交；租户号不从前端接收，由服务端上下文注入。
 */
@Schema(description = "管理后台 - 知识库文档上传 Request VO")
@Data
public class AiKnowledgeDocumentUploadReqVO {

    @Schema(description = "所属知识库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "所属知识库不能为空")
    private Long baseId;

    @Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    @Schema(description = "逻辑文档标识（可选）：同名多次上传按此归为版本链；缺省取规范化文件名", example = "annual-report")
    private String docKey;
}
