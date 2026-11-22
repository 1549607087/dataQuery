package cn.iocoder.boot.module.data_query.controller.admin.model.vo.log;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 模型调用日志分页 Request VO
 */
@Schema(description = "管理后台 - 模型调用日志分页 Request VO")
@Data
public class AiModelCallLogPageReqVO extends PageParam {

    @Schema(description = "全链路追踪 ID", example = "1a2b3c4d")
    private String traceId;

    @Schema(description = "会话编号", example = "1024")
    private Long conversationId;

    @Schema(description = "场景编码", example = "SQL_GEN")
    private String sceneCode;

    @Schema(description = "模型配置编号", example = "2")
    private Long modelConfigId;

    @Schema(description = "模型标识", example = "qwen-plus")
    private String modelId;

    @Schema(description = "状态：0 成功、1 失败、2 超时、3 降级", example = "0")
    private Integer status;

    @Schema(description = "用户编号", example = "1")
    private Long userId;

    @Schema(description = "调用时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
