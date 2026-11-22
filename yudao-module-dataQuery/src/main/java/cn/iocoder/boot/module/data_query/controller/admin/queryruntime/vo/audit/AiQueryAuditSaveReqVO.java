package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 查询审计新增/修改 Request VO
 *
 * tenant_id / creator / updater / create_time / update_time / deleted 一律由服务端写入，
 * 禁止前端传入；主键 id 为空表示新增。
 */
@Schema(description = "管理后台 - 查询审计新增/修改 Request VO")
@Data
public class AiQueryAuditSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "关联 ai_query_session.id，独立提问可为 0", example = "")
    private Long sessionId;

    @Schema(description = "提问用户 ID", example = "")
    private Long userId;

    @Schema(description = "关联 ai_datasource.id，本次问答使用的数据源", example = "")
    private Long datasourceId;

    @Schema(description = "链路追踪 ID，贯穿会话、模型调用与本次审计", example = "")
    private String traceId;

    @Schema(description = "用户原始问题文本", example = "")
    private String question;

    @Schema(description = "改写 / 归一化后的问题文本", example = "")
    private String normalizedQuestion;

    @Schema(description = "意图识别结果编码", example = "")
    private String intentCode;

    @Schema(description = "Schema 召回命中表清单，JSON 数组", example = "")
    private String recalledTables;

    @Schema(description = "关联 ai_prompt_version.id，本次使用的提示词版本", example = "")
    private Long promptVersionId;

    @Schema(description = "关联 ai_model_config.id，本次使用的模型配置", example = "")
    private Long modelConfigId;

    @Schema(description = "LLM 生成的原始 SQL", example = "")
    private String generatedSql;

    @Schema(description = "经静态校验 / 改写后的 SQL", example = "")
    private String validatedSql;

    @Schema(description = "最终下发只读连接执行的 SQL", example = "")
    private String executedSql;

    @Schema(description = "SQL 校验状态：PASS / REJECT / REWRITE 等，由静态校验器写入", example = "")
    private String sqlStatus;

    @Schema(description = "结果返回行数", example = "")
    private Long resultRows;

    @Schema(description = "结果集是否被 max_rows 截断：0 否、1 是", example = "")
    private Integer resultTruncated;

    @Schema(description = "端到端耗时（毫秒）", example = "")
    private Integer latencyMs;

    @Schema(description = "结果状态：1 成功、2 澄清、3 拒绝、4 降级、5 失败", example = "")
    private Integer status;

    @Schema(description = "错误码，成功为空", example = "")
    private String errorCode;

    @Schema(description = "错误摘要，禁止记录完整密码与连接串", example = "")
    private String errorMsg;

    @Schema(description = "是否走只读连接执行：0 否、1 是", example = "")
    private Integer readonlyConnection;

    @Schema(description = "客户端 IP", example = "")
    private String clientIp;

}
