package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 知识库分页 Request VO
 */
@Schema(description = "管理后台 - 知识库分页 Request VO")
@Data
public class AiKnowledgeBasePageReqVO extends PageParam {

    @Schema(description = "知识库名称，模糊匹配", example = "产品")
    private String name;

    @Schema(description = "业务编码，模糊匹配", example = "product")
    private String code;

    @Schema(description = "状态：0 正常、1 停用", example = "0")
    private Integer status;

    @Schema(description = "向量模型代号指纹，精确匹配，如 openai:text-embedding-3-small:1536", example = "openai:text-embedding-3-small:1536")
    private String embeddingModelCode;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
