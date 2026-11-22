package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库切片 Response VO
 *
 * <p>刻意不透出 embedding 字段：1536 维向量塞进列表响应既无意义也会把响应体撑到几 MB。
 * 需要核对向量是否写入时看 embedding_model_code / embedding_dim / sync_status 即可。
 */
@Schema(description = "管理后台 - 知识库切片 Response VO")
@Data
public class AiKnowledgeChunkRespVO {

    @Schema(description = "切片编号", example = "4096")
    private Long id;

    @Schema(description = "知识库编号")
    private Long baseId;

    @Schema(description = "文档编号")
    private Long documentId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "切片序号，从 0 开始")
    private Integer chunkIndex;

    @Schema(description = "切片正文")
    private String content;

    @Schema(description = "内容哈希")
    private String contentHash;

    @Schema(description = "Token 数")
    private Integer tokenCount;

    @Schema(description = "向量模型代号指纹")
    private String embeddingModelCode;

    @Schema(description = "向量维度")
    private Integer embeddingDim;

    @Schema(description = "向量状态：0 待构建 / 1 已构建 / 2 需重建 / 4 已下线")
    private Integer syncStatus;

    @Schema(description = "PDF 页码（TXT/MD 为 null）")
    private Integer pageNo;

    @Schema(description = "标题层级路径")
    private String headingPath;

    @Schema(description = "原文起始字符偏移")
    private Integer charStart;

    @Schema(description = "原文结束字符偏移")
    private Integer charEnd;

    @Schema(description = "状态：0 正常 / 1 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
