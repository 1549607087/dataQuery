package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库文档上传 Response VO
 *
 * 上传接口只做到「落库 + 发 MQ」：返回 documentId 供前端轮询 parse_status，
 * 解析/切片/向量化由消费端异步完成，不在此接口等待结果。
 */
@Schema(description = "管理后台 - 知识库文档上传 Response VO")
@Data
public class AiKnowledgeDocumentUploadRespVO {

    @Schema(description = "文档编号（前端据此轮询解析状态）", example = "2048")
    private Long documentId;

    @Schema(description = "解析状态：0 待解析 / 1 解析中 / 2 已完成 / 3 失败 / 4 已下线", example = "0")
    private Integer parseStatus;

    @Schema(description = "是否命中上传幂等（true 表示复用已存在文档，未重复入库/发消息）")
    private Boolean duplicated;

    @Schema(description = "逻辑文档标识（版本链的键）", example = "annual-report")
    private String docKey;

    @Schema(description = "本次上传对应的文档版本号", example = "1")
    private Integer version;

    @Schema(description = "是否当前生效版本：1 是 / 0 否（新版本消费完成后才切为 1）", example = "0")
    private Integer isLatest;
}
