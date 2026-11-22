package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库问答会话分页 Request VO
 */
@Schema(description = "管理后台 - 知识库问答会话分页 Request VO")
@Data
public class KnowledgeSessionPageReqVO extends PageParam {

    @Schema(description = "知识库编号；不传则返回全部知识库的会话", example = "1024")
    private Long baseId;

    @Schema(description = "标题，模糊匹配")
    private String title;
}
