package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 统一对话提问 Request VO。
 *
 * <p>mode 决定走「智能问数」还是「知识库问答」：
 * <ul>
 *   <li>QUERY：datasourceId 必填，baseId 忽略；</li>
 *   <li>KNOWLEDGE：baseId 必填，datasourceId 忽略。</li>
 * </ul>
 * sessionId 续聊时必传，且 mode / 归属资源必须与会话一致，否则服务端拒绝——
 * 绝不允许一个会话里既挂 datasourceId 又挂 baseId（两条互斥的归属链）。
 */
@Schema(description = "管理后台 - 统一对话提问 Request VO")
@Data
public class UnifiedChatAskReqVO {

    @Schema(description = "业务类型：QUERY 智能问数 / KNOWLEDGE 知识库问答", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务类型不能为空")
    @Pattern(regexp = "QUERY|KNOWLEDGE", message = "业务类型只能是 QUERY 或 KNOWLEDGE")
    private String mode;

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "上月会议室使用情况")
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "会话编号；不传则新建会话（切换 mode 必须新建，不能携带旧 sessionId）", example = "2048")
    private Long sessionId;

    @Schema(description = "数据源编号（mode=QUERY 必填）", example = "10")
    private Long datasourceId;

    @Schema(description = "知识库编号（mode=KNOWLEDGE 必填）", example = "1024")
    private Long baseId;

    @Schema(description = "模型配置编号（可选，仅作为路由偏好）")
    private Long modelConfigId;

    @Schema(description = "场景编码（可选，仅 QUERY 有效，默认 SQL_GEN）")
    private String sceneCode;

    @Schema(description = "文档版本水位（可选，仅 KNOWLEDGE 有效）：回退到 version<=N 的最高版本；不传取当前生效版本")
    private Integer version;

    @Schema(description = "客户端请求编号（可选）：流式提问时前端生成并携带，用于点「停止」时定位在途请求；不传则本次提问不可取消")
    @Size(max = 64, message = "客户端请求编号长度不能超过 64 字符")
    private String clientRequestId;
}
