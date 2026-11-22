package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识库问答 - 提问 Response VO（P9.2）
 *
 * <p>终态 {@code status} 取值与问数一致：DONE / CLARIFY / REJECT / DEGRADED / FAILED。
 * 无论哪种终态都返回 {@code answer}（澄清话术、拒绝说明、降级原因），前端直接渲染即可，
 * 不需要按状态分支处理空回答。
 *
 * <p>{@code references} 只包含回答中真实引用到的切片，{@code citationDroppedCount}
 * 表示模型编造、被服务端删除的越界编号数量，前端可据此提示「部分引用不可用」。
 */
@Schema(description = "管理后台 - 知识库问答提问 Response VO")
@Data
public class AiKnowledgeChatRespVO {

    @Schema(description = "会话编号", example = "2048")
    private Long sessionId;

    @Schema(description = "会话业务编号", example = "KS1730000000000ABCDEF")
    private String sessionNo;

    @Schema(description = "全链路追踪编号")
    private String traceId;

    @Schema(description = "知识库编号", example = "1024")
    private Long baseId;

    @Schema(description = "知识库名称")
    private String baseName;

    @Schema(description = "用户问题")
    private String question;

    @Schema(description = "终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED")
    private String status;

    @Schema(description = "助手回答（已做引用编号校验）")
    private String answer;

    @Schema(description = "引用溯源列表")
    private List<KnowledgeReference> references;

    @Schema(description = "召回方式：VECTOR / HYBRID / KEYWORD_DEGRADED / EMPTY")
    private String recallMode;

    @Schema(description = "召回是否降级")
    private Boolean recallDegraded;

    @Schema(description = "召回降级原因")
    private String recallDegradeReason;

    @Schema(description = "进入上下文的切片数（裁剪后）")
    private Integer retrievedCount;

    @Schema(description = "被删除的越界引用编号数量")
    private Integer citationDroppedCount;

    @Schema(description = "提示词版本编号")
    private Long promptVersionId;

    @Schema(description = "模型标识")
    private String modelId;

    @Schema(description = "模型配置编号")
    private Long modelConfigId;

    @Schema(description = "本轮耗时（毫秒）")
    private Integer latencyMs;

    @Schema(description = "业务错误码")
    private Integer errorCode;

    @Schema(description = "错误原因")
    private String errorMsg;

    @Schema(description = "降级 / 提示信息")
    private String degradeReason;

    @Schema(description = "本轮 USER 消息编号（落库失败为 null）")
    private Long userMessageId;

    @Schema(description = "本轮 ASSISTANT 消息编号（落库失败为 null）")
    private Long assistantMessageId;
}
