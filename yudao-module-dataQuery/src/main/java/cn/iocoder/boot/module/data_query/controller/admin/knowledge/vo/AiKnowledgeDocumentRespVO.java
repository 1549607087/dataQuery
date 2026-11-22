package cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 知识库文档 Response VO
 *
 * 含 parse_status / parse_error / chunk_count 供前端上传后轮询解析进度。
 */
@Schema(description = "管理后台 - 知识库文档 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiKnowledgeDocumentRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "所属知识库编号")
    @ExcelProperty("所属知识库")
    private Long baseId;

    @Schema(description = "文件名")
    @ExcelProperty("文件名")
    private String fileName;

    @Schema(description = "扩展名（大写）")
    @ExcelProperty("类型")
    private String fileType;

    @Schema(description = "文件字节数")
    @ExcelProperty("大小(字节)")
    private Long fileSize;

    @Schema(description = "文件存储位置")
    private String fileUrl;

    @Schema(description = "内容 SHA256")
    private String contentHash;

    @Schema(description = "逻辑文档标识（同 docKey 为同一文档的多个版本）")
    @ExcelProperty("逻辑文档标识")
    private String docKey;

    @Schema(description = "文档版本号（同 docKey 内从 1 递增）", example = "1")
    @ExcelProperty("版本")
    private Integer version;

    @Schema(description = "是否当前生效版本：1 是 / 0 历史版本", example = "1")
    @ExcelProperty("生效版本")
    private Integer isLatest;

    @Schema(description = "解析状态：0 待解析 / 1 解析中 / 2 已完成 / 3 失败 / 4 已下线")
    @ExcelProperty("解析状态")
    private Integer parseStatus;

    @Schema(description = "解析失败原因")
    private String parseError;

    @Schema(description = "切片数")
    @ExcelProperty("切片数")
    private Integer chunkCount;

    @Schema(description = "纯文本字符数")
    private Long charCount;

    @Schema(description = "是否参与检索：1 是 / 0 否")
    private Integer enabled;

    @Schema(description = "创建人")
    private String creator;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
