package cn.iocoder.boot.module.data_query.controller.admin.model.vo.binding;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 模型场景绑定分页 Request VO
 */
@Schema(description = "管理后台 - 模型场景绑定分页 Request VO")
@Data
public class AiModelSceneBindingPageReqVO extends PageParam {

    @Schema(description = "场景编码，精确匹配", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "场景名称，模糊匹配", example = "SQL 生成")
    private String sceneName;

    @Schema(description = "主模型编号", example = "2")
    private Long primaryModelId;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
