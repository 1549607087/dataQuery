package cn.iocoder.boot.module.data_query.controller.admin.model.vo.config;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 模型配置分页 Request VO
 */
@Schema(description = "管理后台 - 模型配置分页 Request VO")
@Data
public class AiModelConfigPageReqVO extends PageParam {

    @Schema(description = "模型显示名称，模糊匹配", example = "SQL 生成模型")
    private String name;

    @Schema(description = "模型标识，模糊匹配", example = "qwen-plus")
    private String modelId;

    @Schema(description = "供应商编号", example = "1")
    private Long providerId;

    @Schema(description = "类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用", example = "1")
    private Integer modelType;

    @Schema(description = "状态：0 启用、1 停用、2 灰度", example = "0")
    private Integer status;

    @Schema(description = "健康状态：0 未检测、1 健康、2 异常", example = "1")
    private Integer healthStatus;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
