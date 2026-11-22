package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 提示词模板变量（占位符）Response VO
 */
@Schema(description = "管理后台 - 提示词模板变量 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiPromptVariableRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("提示词编号")
    private Long promptId;

    @Schema(description = "场景编码")
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "占位符名", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("占位符名")
    private String varName;

    @Schema(description = "占位符中文名")
    @ExcelProperty("中文名")
    private String varLabel;

    @Schema(description = "值类型：1 文本、2 数字、3 日期、4 枚举、5 JSON、6 SQL 片段、7 表结构、8 结果集")
    @ExcelProperty("值类型")
    private Integer varType;

    @Schema(description = "注入来源")
    @ExcelProperty("注入来源")
    private Integer sourceType;

    @Schema(description = "来源键")
    @ExcelProperty("来源键")
    private String sourceKey;

    @Schema(description = "是否必填：0 否、1 是")
    @ExcelProperty("必填")
    private Integer required;

    @Schema(description = "默认值")
    @ExcelProperty("默认值")
    private String defaultValue;

    @Schema(description = "示例值")
    @ExcelProperty("示例值")
    private String sampleValue;

    @Schema(description = "值校验正则")
    private String validateRegex;

    @Schema(description = "渲染时最大字符数")
    @ExcelProperty("最大长度")
    private Integer maxLength;

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
