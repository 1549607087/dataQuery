package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 提示词模板变量（占位符）新增/修改 Request VO
 */
@Schema(description = "管理后台 - 提示词模板变量新增/修改 Request VO")
@Data
public class AiPromptVariableSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "提示词编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "提示词编号不能为空")
    private Long promptId;

    @Schema(description = "场景编码冗余", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "占位符名，不含双花括号", requiredMode = Schema.RequiredMode.REQUIRED, example = "question")
    @NotEmpty(message = "占位符名不能为空")
    private String varName;

    @Schema(description = "占位符中文名", example = "用户问题")
    private String varLabel;

    @Schema(description = "值类型：1 文本、2 数字、3 日期、4 枚举、5 JSON、6 SQL 片段、7 表结构、8 结果集", example = "1")
    private Integer varType;

    @Schema(description = "注入来源：1 系统上下文、2 用户输入、3 元数据召回、4 上一步结果、5 字典/配置", example = "2")
    private Integer sourceType;

    @Schema(description = "来源键", example = "user.question")
    private String sourceKey;

    @Schema(description = "是否必填：0 否、1 是", example = "1")
    private Integer required;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "示例值")
    private String sampleValue;

    @Schema(description = "值校验正则，为空则不校验")
    private String validateRegex;

    @Schema(description = "渲染时最大字符数，0 表示不限")
    private Integer maxLength;

    @Schema(description = "状态：0 启用、1 停用", example = "0")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

}
