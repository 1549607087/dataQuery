package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 提示词场景字典 Response VO
 */
@Schema(description = "管理后台 - 提示词场景字典 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiPromptSceneRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "场景名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("场景名称")
    private String sceneName;

    @Schema(description = "场景用途说明")
    @ExcelProperty("用途说明")
    private String description;

    @Schema(description = "默认占位符集合 JSON 数组")
    private String defaultVariables;

    @Schema(description = "建议模型类型：1 Chat、4 SQL 专用")
    @ExcelProperty("建议模型类型")
    private Integer defaultModelType;

    @Schema(description = "状态：0 启用、1 停用")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
