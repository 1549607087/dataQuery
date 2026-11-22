package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 提示词模板变量（占位符）分页 Request VO
 */
@Schema(description = "管理后台 - 提示词模板变量分页 Request VO")
@Data
public class AiPromptVariablePageReqVO extends PageParam {

    @Schema(description = "提示词编号", example = "1024")
    private Long promptId;

    @Schema(description = "场景编码", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "占位符名，模糊匹配", example = "question")
    private String varName;

    @Schema(description = "占位符中文名，模糊匹配", example = "用户问题")
    private String varLabel;

    @Schema(description = "值类型：1 文本、2 数字、3 日期、4 枚举、5 JSON、6 SQL 片段、7 表结构、8 结果集", example = "1")
    private Integer varType;

    @Schema(description = "注入来源：1 系统上下文、2 用户输入、3 元数据召回、4 上一步结果、5 字典/配置", example = "2")
    private Integer sourceType;

    @Schema(description = "是否必填：0 否、1 是", example = "1")
    private Integer required;

    @Schema(description = "状态：0 启用、1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
