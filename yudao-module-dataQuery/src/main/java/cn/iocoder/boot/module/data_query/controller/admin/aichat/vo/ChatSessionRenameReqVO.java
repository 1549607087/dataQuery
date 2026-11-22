package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理后台 - 会话改名请求（P7） */
@Schema(description = "管理后台 - 会话改名请求")
@Data
public class ChatSessionRenameReqVO {

    @NotNull(message = "会话编号不能为空")
    private Long sessionId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100")
    private String title;
}
