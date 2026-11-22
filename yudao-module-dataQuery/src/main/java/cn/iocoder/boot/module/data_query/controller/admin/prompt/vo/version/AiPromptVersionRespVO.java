package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 提示词版本 Response VO
 */
@Schema(description = "管理后台 - 提示词版本 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiPromptVersionRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("提示词编号")
    private Long promptId;

    @Schema(description = "版本号")
    @ExcelProperty("版本号")
    private Integer version;

    @Schema(description = "该版本模板正文快照")
    private String content;

    @Schema(description = "该版本变量定义快照")
    private String variableDefs;

    @Schema(description = "该版本模型参数快照")
    private String params;

    @Schema(description = "变更说明")
    @ExcelProperty("变更说明")
    private String changeLog;

    @Schema(description = "正文哈希")
    private String contentHash;

    @Schema(description = "Token 预估值")
    @ExcelProperty("Token 预估")
    private Integer tokenEstimate;

    @Schema(description = "状态：0 草稿、1 已发布、2 已回滚、3 归档")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "发布时间")
    @ExcelProperty("发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "发布人")
    @ExcelProperty("发布人")
    private String publisher;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
