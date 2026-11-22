package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 统一对话操作请求 VO 集合。
 *
 * <p>把重新生成 / 修改问题 / 版本切换 / 会话改名删除 / 赞踩的请求聚在一个文件里，
 * 避免为几个 3~4 字段的小对象各建一个文件。每个静态内部类对应一个端点。
 */
public final class ChatOperateReqVO {

    private ChatOperateReqVO() {
    }

    /** 重新生成：以原问题重跑，旧版本退出主链 */
    @Schema(description = "管理后台 - 统一对话重新生成 Request VO")
    @Data
    public static class Regenerate {
        @Schema(description = "业务类型：QUERY / KNOWLEDGE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "业务类型不能为空")
        @Pattern(regexp = "QUERY|KNOWLEDGE", message = "业务类型只能是 QUERY 或 KNOWLEDGE")
        private String mode;

        @Schema(description = "助手消息编号（以该轮问题重跑）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "消息编号不能为空")
        private Long messageId;

        @Schema(description = "模型配置编号（可选，仅作为路由偏好）")
        private Long modelConfigId;
    }

    /** 修改问题：改写问题后从该轮重跑，生成新分支 */
    @Schema(description = "管理后台 - 统一对话修改问题 Request VO")
    @Data
    public static class Edit {
        @Schema(description = "业务类型：QUERY / KNOWLEDGE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "业务类型不能为空")
        @Pattern(regexp = "QUERY|KNOWLEDGE", message = "业务类型只能是 QUERY 或 KNOWLEDGE")
        private String mode;

        @Schema(description = "用户消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "消息编号不能为空")
        private Long messageId;

        @Schema(description = "改写后的问题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题长度不能超过 500 字符")
        private String question;

        @Schema(description = "模型配置编号（可选，仅作为路由偏好）")
        private Long modelConfigId;
    }

    /** 赞踩：-1 踩 / 0 取消 / 1 赞 */
    @Schema(description = "管理后台 - 统一对话赞踩 Request VO")
    @Data
    public static class Rating {
        @Schema(description = "业务类型：QUERY / KNOWLEDGE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "业务类型不能为空")
        @Pattern(regexp = "QUERY|KNOWLEDGE", message = "业务类型只能是 QUERY 或 KNOWLEDGE")
        private String mode;

        @Schema(description = "助手消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "消息编号不能为空")
        private Long messageId;

        @Schema(description = "评分：-1 踩 / 0 取消 / 1 赞", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "评分不能为空")
        @Min(value = -1, message = "评分最小为 -1")
        @Max(value = 1, message = "评分最大为 1")
        private Integer rating;
    }

    /** 版本切换：只改兄弟选中与 current_leaf_id，不重跑链路 */
    @Schema(description = "管理后台 - 统一对话版本切换 Request VO")
    @Data
    public static class SwitchVersion {
        @Schema(description = "业务类型：QUERY / KNOWLEDGE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "业务类型不能为空")
        @Pattern(regexp = "QUERY|KNOWLEDGE", message = "业务类型只能是 QUERY 或 KNOWLEDGE")
        private String mode;

        @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "会话编号不能为空")
        private Long sessionId;

        @Schema(description = "目标消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "目标消息编号不能为空")
        private Long targetMessageId;
    }

    /** 会话改名 */
    @Schema(description = "管理后台 - 统一对话会话改名 Request VO")
    @Data
    public static class Rename {
        @Schema(description = "业务类型：QUERY / KNOWLEDGE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "业务类型不能为空")
        @Pattern(regexp = "QUERY|KNOWLEDGE", message = "业务类型只能是 QUERY 或 KNOWLEDGE")
        private String mode;

        @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "会话编号不能为空")
        private Long sessionId;

        @Schema(description = "新标题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题长度不能超过 200 字符")
        private String title;
    }
}
