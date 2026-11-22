package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库问答 - 提问 Request VO（P9.2）
 *
 * <p>前端只能传这四项：知识库、会话、问题、模型偏好。
 * 检索上下文 / 历史 / 提示词模板均由服务端生成，不接受前端注入。
 */
@Schema(description = "管理后台 - 知识库问答提问 Request VO")
@Data
public class AiKnowledgeChatAskReqVO {

    @Schema(description = "知识库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "知识库编号不能为空")
    private Long baseId;

    @Schema(description = "会话编号；不传则新建会话", example = "2048")
    private Long sessionId;

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "报销流程是什么")
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "模型配置编号（可选，仅作为路由偏好，仍受 RAG 场景候选集约束）")
    private Long modelConfigId;

    @Schema(description = "文档版本水位（可选）：回退到 version<=N 的最高版本；不传取当前生效版本")
    private Integer version;
}
