package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 知识库文档分页 Request VO
 */
@Schema(description = "管理后台 - 知识库文档分页 Request VO")
@Data
public class AiKnowledgeDocumentPageReqVO extends PageParam {

    @Schema(description = "所属知识库编号", example = "1024")
    private Long baseId;

    @Schema(description = "文件名，模糊匹配")
    private String fileName;

    @Schema(description = "逻辑文档标识，精确匹配（查同一文档的所有版本）", example = "annual-report")
    private String docKey;

    @Schema(description = "文档版本号，精确匹配", example = "1")
    private Integer version;

    @Schema(description = "扩展名（大写），精确匹配", example = "PDF")
    private String fileType;

    @Schema(description = "解析状态：0 待解析 / 1 解析中 / 2 已完成 / 3 失败 / 4 已下线")
    private Integer parseStatus;

    @Schema(description = "是否参与检索")
    private Integer enabled;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
