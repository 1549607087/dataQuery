package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 提示词版本对比 Response VO
 *
 * 返回两个版本的原始内容与变量快照，由前端做逐行 diff。
 */
@Schema(description = "管理后台 - 提示词版本对比 Response VO")
@Data
public class AiPromptVersionDiffRespVO {

    @Schema(description = "对比源版本编号")
    private Long sourceVersionId;

    @Schema(description = "对比目标版本编号")
    private Long targetVersionId;

    @Schema(description = "源版本号")
    private Integer sourceVersion;

    @Schema(description = "目标版本号")
    private Integer targetVersion;

    @Schema(description = "源版本正文")
    private String sourceContent;

    @Schema(description = "目标版本正文")
    private String targetContent;

    @Schema(description = "源版本变量定义快照")
    private String sourceVariableDefs;

    @Schema(description = "目标版本变量定义快照")
    private String targetVariableDefs;

    @Schema(description = "正文是否发生变化")
    private Boolean contentChanged;

    @Schema(description = "变量定义是否发生变化")
    private Boolean variableChanged;

}
