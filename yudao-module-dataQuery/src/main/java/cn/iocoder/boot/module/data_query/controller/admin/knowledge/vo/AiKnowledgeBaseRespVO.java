package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 知识库 Response VO
 */
@Schema(description = "管理后台 - 知识库 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiKnowledgeBaseRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "知识库名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("知识库名称")
    private String name;

    @Schema(description = "业务编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("业务编码")
    private String code;

    @Schema(description = "描述")
    @ExcelProperty("描述")
    private String description;

    @Schema(description = "状态：0 正常、1 停用")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "文档数（冗余统计）")
    @ExcelProperty("文档数")
    private Integer documentCount;

    @Schema(description = "切片数（冗余统计）")
    @ExcelProperty("切片数")
    private Integer chunkCount;

    @Schema(description = "向量模型代号指纹，格式 provider.code:model_id:dimension，如 openai:text-embedding-3-small:1536；系统写入，创建/更新接口不接受")
    @ExcelProperty("向量模型代号")
    private String embeddingModelCode;

    @Schema(description = "创建人")
    @ExcelProperty("创建人")
    private String creator;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
