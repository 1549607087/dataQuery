package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.session;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 问数会话分页 Request VO
 */
@Schema(description = "管理后台 - 问数会话分页 Request VO")
@Data
public class AiQuerySessionPageReqVO extends PageParam {

    @Schema(description = "提问用户 ID")
    private Long userId;

    @Schema(description = "关联 ai_datasource.id，本次会话绑定的数据源")
    private Long datasourceId;

    @Schema(description = "会话编号，租户内唯一，便于检索与对外引用，模糊匹配")
    private String sessionNo;

    @Schema(description = "场景编码，关联 ai_prompt_scene.scene_code")
    private String sceneCode;

    @Schema(description = "状态：1 进行中、2 已结束、3 归档")
    private Integer status;

    @Schema(description = "会话标题，可由首问自动生成或用户修改，模糊匹配")
    private String title;

    @Schema(description = "链路追踪 ID，关联 ai_query_audit.trace_id")
    private String traceId;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
