package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 提示词场景字典分页 Request VO
 */
@Schema(description = "管理后台 - 提示词场景字典分页 Request VO")
@Data
public class AiPromptScenePageReqVO extends PageParam {

    @Schema(description = "场景编码，模糊匹配", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "场景名称，模糊匹配", example = "SQL 生成")
    private String sceneName;

    @Schema(description = "状态：0 启用、1 停用", example = "0")
    private Integer status;

    @Schema(description = "建议模型类型：1 Chat、4 SQL 专用", example = "1")
    private Integer defaultModelType;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
