package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 问数会话 Response VO
 *
 * 不回显 tenantId / deleted / creator / updater 等内部字段；
 * 审计表内容可能含 SQL 文本，除上述内部字段外完整回显。
 */
@Schema(description = "管理后台 - 问数会话 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiQuerySessionRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "提问用户 ID")
    @ExcelProperty("提问用户 ID")
    private Long userId;

    @Schema(description = "关联 ai_datasource.id，本次会话绑定的数据源")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "会话标题，可由首问自动生成或用户修改")
    @ExcelProperty("会话标题")
    private String title;

    @Schema(description = "会话编号，租户内唯一，便于检索与对外引用")
    @ExcelProperty("会话编号")
    private String sessionNo;

    @Schema(description = "场景编码，关联 ai_prompt_scene.scene_code")
    @ExcelProperty("场景编码")
    private String sceneCode;

    @Schema(description = "状态：1 进行中、2 已结束、3 归档")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "消息条数，含用户提问与助手回答")
    @ExcelProperty("消息条数")
    private Integer messageCount;

    @Schema(description = "最近一次用户问题，用于会话列表预览")
    @ExcelProperty("最近一次用户问题")
    private String lastQuestion;

    @Schema(description = "最近一次提问时间")
    @ExcelProperty("最近一次提问时间")
    private LocalDateTime lastAskTime;

    @Schema(description = "链路追踪 ID，关联 ai_query_audit.trace_id")
    @ExcelProperty("链路追踪 ID")
    private String traceId;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
