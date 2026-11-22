package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库切片分页 Request VO
 */
@Schema(description = "管理后台 - 知识库切片分页 Request VO")
@Data
public class AiKnowledgeChunkPageReqVO extends PageParam {

    @Schema(description = "知识库编号", example = "1024")
    private Long baseId;

    @Schema(description = "文档编号", example = "2048")
    private Long documentId;

    @Schema(description = "文件名，模糊匹配")
    private String fileName;

    @Schema(description = "向量状态：0 待构建 / 1 已构建 / 2 需重建 / 4 已下线")
    private Integer syncStatus;
}
