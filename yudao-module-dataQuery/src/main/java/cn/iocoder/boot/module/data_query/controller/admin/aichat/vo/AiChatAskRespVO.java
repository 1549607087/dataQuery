package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import cn.iocoder.boot.module.data_query.framework.chat.ChartSpec;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 智能问数 ask 响应 VO（P5）。
 *
 * 一次问数返回：终态 + 表格结果 + SQL + 可解释信息（命中表、提示词版本、模型、
 * 召回方式、struct_version、traceId、耗时）。前端据 status 决定渲染分支：
 * DONE 渲染表格/SQL；CLARIFY 展示澄清提示；REJECT/DEGRADED/FAILED 展示原因。
 *
 * 不回显 tenantId、连接串、凭证等内部字段。
 */
@Schema(description = "管理后台 - 智能问数 ask 响应")
@Data
public class AiChatAskRespVO {

    @Schema(description = "终态：DONE 成功 / CLARIFY 需澄清 / REJECT 拒绝 / DEGRADED 降级 / FAILED 失败")
    private String status;

    @Schema(description = "会话编号")
    private Long sessionId;

    @Schema(description = "消息编号（兼容 P5 的 sessionId:traceId 组合串）")
    private String messageId;

    @Schema(description = "P7 对话消息编号：本轮用户消息 / 助手消息（落库成功时非空，供重新生成、赞踩引用）")
    private Long userMessageId;
    private Long assistantMessageId;

    @Schema(description = "链路追踪 ID")
    private String traceId;

    @Schema(description = "原始问题 / 规范化后的问题 / 意图编码")
    private String question;
    private String normalizedQuestion;
    private String intentCode;

    @Schema(description = "A 方案追问改写：是否把当前问题结合会话历史补全后再召回")
    private Boolean rewriteApplied;
    @Schema(description = "A 方案追问改写：改写来源（HISTORY_RULE）；未改写为空")
    private String rewriteSource;
    @Schema(description = "A 方案追问改写：被参考的上一轮问题；未改写为空")
    private String prevQuestion;

    @Schema(description = "最终 SQL（优先 executed，其次 validated / generated）")
    private String sql;
    @Schema(description = "模型生成的原始 SQL")
    private String generatedSql;
    @Schema(description = "最终执行 SQL")
    private String executedSql;

    @Schema(description = "查询结果（表格 + 列元信息 + 截断/脱敏标记 + 审计编号）")
    private QueryResult result;

    @Schema(description = "S3 召回方式 / 是否降级 / 降级原因 / 命中表数 / 结构版本")
    private String recallMode;
    private Boolean recallDegraded;
    private String recallDegradeReason;
    private Integer recallTableCount;
    private Integer structVersion;
    @Schema(description = "外键一跳扩展带进来的表编号（如 oa_user，便于观测维表为何出现）")
    private java.util.List<Long> relationExpandedTableIds;

    @Schema(description = "提示词版本快照编号 / 正文哈希")
    private Long promptVersionId;
    private String promptContentHash;

    @Schema(description = "实际使用的模型标识 / 模型配置编号")
    private String modelId;
    private Long modelConfigId;

    @Schema(description = "结果摘要（S8，可空）/ 降级说明")
    private String summary;
    private String degradeReason;

    @Schema(description = "P6 S8 规则图表推荐（纯规则，不调用模型；失败降级为 null）")
    private ChartSpec chartSpec;

    @Schema(description = "P6 S8 结构化口径说明（不输出业务数据行；失败降级为 null）")
    private String explain;

    @Schema(description = "错误码 / 错误原因（终态非 DONE 时）")
    private Integer errorCode;
    private String errorMsg;

    @Schema(description = "模型修正轮次（SQL_FIX + S6/S7 共用预算）")
    private Integer fixRounds;

    @Schema(description = "端到端耗时（毫秒）")
    private Integer latencyMs;

}
