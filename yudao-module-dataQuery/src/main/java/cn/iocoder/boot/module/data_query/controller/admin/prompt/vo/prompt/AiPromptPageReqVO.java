package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 提示词模板分页 Request VO
 */
@Schema(description = "管理后台 - 提示词模板分页 Request VO")
@Data
public class AiPromptPageReqVO extends PageParam {

    @Schema(description = "场景编码，精确匹配", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "提示词名称，模糊匹配", example = "SQL 生成")
    private String name;

    @Schema(description = "提示词编码，模糊匹配", example = "PROMPT_SQL_GEN_DEFAULT")
    private String code;

    @Schema(description = "类型：1 System、2 User、3 完整模板", example = "1")
    private Integer promptType;

    @Schema(description = "状态：0 启用、1 停用、2 灰度", example = "0")
    private Integer status;

    @Schema(description = "来源：1 平台内置、2 租户自定义、3 复制自平台模板", example = "1")
    private Integer source;

    @Schema(description = "默认模型编号", example = "2")
    private Long modelId;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
