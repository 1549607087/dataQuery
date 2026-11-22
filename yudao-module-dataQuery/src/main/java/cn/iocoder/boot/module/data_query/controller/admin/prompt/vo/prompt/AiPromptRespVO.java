package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 提示词模板 Response VO
 */
@Schema(description = "管理后台 - 提示词模板 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiPromptRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "提示词名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("提示词名称")
    private String name;

    @Schema(description = "提示词编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("提示词编码")
    private String code;

    @Schema(description = "类型：1 System、2 User、3 完整模板")
    @ExcelProperty("类型")
    private Integer promptType;

    @Schema(description = "模板正文，占位符统一使用 {{var_name}}")
    private String content;

    @Schema(description = "变量定义只读快照 JSON")
    private String variableDefs;

    @Schema(description = "默认模型编号")
    @ExcelProperty("默认模型编号")
    private Long modelId;

    @Schema(description = "模型参数覆盖 JSON")
    private String params;

    @Schema(description = "来源：1 平台内置、2 租户自定义、3 复制自平台模板")
    @ExcelProperty("来源")
    private Integer source;

    @Schema(description = "继承/复制来源编号")
    @ExcelProperty("来源模板编号")
    private Long parentId;

    @Schema(description = "当前版本号")
    @ExcelProperty("当前版本")
    private Integer version;

    @Schema(description = "当前生效版本编号")
    @ExcelProperty("生效版本编号")
    private Long currentVersionId;

    @Schema(description = "状态：0 启用、1 停用、2 灰度")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "灰度流量比例 0-100")
    @ExcelProperty("灰度比例")
    private Integer grayRatio;

    @Schema(description = "同场景内优先级")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "租户编号")
    @ExcelProperty("租户编号")
    private Long tenantId;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
