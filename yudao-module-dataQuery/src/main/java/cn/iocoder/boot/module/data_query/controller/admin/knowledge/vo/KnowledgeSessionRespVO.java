package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库问答会话 Response VO
 *
 * <p>只透出会话列表 / 详情需要的字段：知识库绑定、标题、状态、轮次计数、
 * 最近问题与时间（列表排序键）、当前叶子（前端判断是否需要继续滚动加载）。
 */
@Schema(description = "管理后台 - 知识库问答会话 Response VO")
@Data
public class KnowledgeSessionRespVO {

    @Schema(description = "会话编号", example = "2048")
    private Long id;

    @Schema(description = "知识库编号", example = "1024")
    private Long baseId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话业务编号")
    private String sessionNo;

    @Schema(description = "场景编码，固定 RAG")
    private String sceneCode;

    @Schema(description = "状态：1 进行中 / 2 已结束")
    private Integer status;

    @Schema(description = "消息条数（每轮 +2）")
    private Integer messageCount;

    @Schema(description = "最近一次问题")
    private String lastQuestion;

    @Schema(description = "最近一次提问时间")
    private LocalDateTime lastAskTime;

    @Schema(description = "当前激活分支末端消息编号")
    private Long currentLeafId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
