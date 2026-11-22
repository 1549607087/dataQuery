package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库问答消息 Response VO
 *
 * <p>USER 消息只用 question，ASSISTANT 消息用 answer + references + 召回质量指标。
 * 两条消息共享同一 seq 的相邻值（USER=seq、ASSISTANT=seq+1），前端按 seq 升序渲染即可。
 */
@Schema(description = "管理后台 - 知识库问答消息 Response VO")
@Data
public class KnowledgeChatMessageRespVO {

    @Schema(description = "消息编号", example = "4096")
    private Long id;

    @Schema(description = "角色：USER / ASSISTANT")
    private String role;

    @Schema(description = "直接前驱消息编号")
    private Long parentId;

    @Schema(description = "同一 parent + role 下的版本号")
    private Integer version;

    @Schema(description = "会话内展示序号")
    private Integer seq;

    @Schema(description = "是否在当前主链上")
    private Boolean active;

    @Schema(description = "用户问题")
    private String question;

    @Schema(description = "助手回答")
    private String answer;

    @Schema(description = "引用溯源列表")
    private List<KnowledgeReference> references;

    @Schema(description = "召回方式")
    private String recallMode;

    @Schema(description = "召回是否降级")
    private Boolean recallDegraded;

    @Schema(description = "进入上下文的切片数")
    private Integer retrievedCount;

    @Schema(description = "被删除的越界引用编号数量")
    private Integer citationDroppedCount;

    @Schema(description = "本轮耗时（毫秒）")
    private Integer latencyMs;

    @Schema(description = "终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED")
    private String status;

    @Schema(description = "业务错误码")
    private String errorCode;

    @Schema(description = "错误原因")
    private String errorMsg;

    @Schema(description = "赞踩：-1 踩 / 0 未评价 / 1 赞")
    private Integer rating;

    @Schema(description = "全链路追踪编号")
    private String traceId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
