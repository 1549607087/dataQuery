package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.session;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 问数会话新增/修改 Request VO
 *
 * tenant_id / creator / updater / create_time / update_time / deleted 一律由服务端写入，
 * 禁止前端传入；主键 id 为空表示新增。
 */
@Schema(description = "管理后台 - 问数会话新增/修改 Request VO")
@Data
public class AiQuerySessionSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "提问用户 ID", example = "")
    private Long userId;

    @Schema(description = "关联 ai_datasource.id，本次会话绑定的数据源", example = "")
    private Long datasourceId;

    @Schema(description = "会话标题，可由首问自动生成或用户修改", example = "")
    private String title;

    @Schema(description = "会话编号，租户内唯一，便于检索与对外引用", example = "")
    @NotEmpty(message = "SessionNo不能为空")
    private String sessionNo;

    @Schema(description = "场景编码，关联 ai_prompt_scene.scene_code", example = "")
    private String sceneCode;

    @Schema(description = "状态：1 进行中、2 已结束、3 归档", example = "")
    private Integer status;

    @Schema(description = "消息条数，含用户提问与助手回答", example = "")
    private Integer messageCount;

    @Schema(description = "最近一次用户问题，用于会话列表预览", example = "")
    private String lastQuestion;

    @Schema(description = "最近一次提问时间", example = "")
    private LocalDateTime lastAskTime;

    @Schema(description = "链路追踪 ID，关联 ai_query_audit.trace_id", example = "")
    private String traceId;

    @Schema(description = "备注", example = "")
    private String remark;

}
