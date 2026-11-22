package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 查询审计 Response VO
 *
 * 不回显 tenantId / deleted / creator / updater 等内部字段；
 * 审计表内容可能含 SQL 文本，除上述内部字段外完整回显。
 */
@Schema(description = "管理后台 - 查询审计 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiQueryAuditRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_query_session.id，独立提问可为 0")
    @ExcelProperty("关联 ai_query_se")
    private Long sessionId;

    @Schema(description = "提问用户 ID")
    @ExcelProperty("提问用户 ID")
    private Long userId;

    @Schema(description = "关联 ai_datasource.id，本次问答使用的数据源")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "链路追踪 ID，贯穿会话、模型调用与本次审计")
    @ExcelProperty("链路追踪 ID")
    private String traceId;

    @Schema(description = "用户原始问题文本")
    @ExcelProperty("用户原始问题文本")
    private String question;

    @Schema(description = "改写 / 归一化后的问题文本")
    @ExcelProperty("改写 / 归一化后的问题文本")
    private String normalizedQuestion;

    @Schema(description = "意图识别结果编码")
    @ExcelProperty("意图识别结果编码")
    private String intentCode;

    @Schema(description = "Schema 召回命中表清单，JSON 数组")
    @ExcelProperty("Schema 召回命中表清单")
    private String recalledTables;

    @Schema(description = "关联 ai_prompt_version.id，本次使用的提示词版本")
    @ExcelProperty("关联 ai_prompt_v")
    private Long promptVersionId;

    @Schema(description = "关联 ai_model_config.id，本次使用的模型配置")
    @ExcelProperty("关联 ai_model_co")
    private Long modelConfigId;

    @Schema(description = "LLM 生成的原始 SQL")
    @ExcelProperty("LLM 生成的原始 SQL")
    private String generatedSql;

    @Schema(description = "经静态校验 / 改写后的 SQL")
    @ExcelProperty("经静态校验 / 改写后的 S")
    private String validatedSql;

    @Schema(description = "最终下发只读连接执行的 SQL")
    @ExcelProperty("最终下发只读连接执行的 SQ")
    private String executedSql;

    @Schema(description = "SQL 校验状态：PASS / REJECT / REWRITE 等，由静态校验器写入")
    @ExcelProperty("SQL 校验状态")
    private String sqlStatus;

    @Schema(description = "结果返回行数")
    @ExcelProperty("结果返回行数")
    private Long resultRows;

    @Schema(description = "结果集是否被 max_rows 截断：0 否、1 是")
    @ExcelProperty("结果集是否被 max_row")
    private Integer resultTruncated;

    @Schema(description = "端到端耗时（毫秒）")
    @ExcelProperty("端到端耗时（毫秒）")
    private Integer latencyMs;

    @Schema(description = "结果状态：1 成功、2 澄清、3 拒绝、4 降级、5 失败")
    @ExcelProperty("结果状态")
    private Integer status;

    @Schema(description = "错误码，成功为空")
    @ExcelProperty("错误码")
    private String errorCode;

    @Schema(description = "错误摘要，禁止记录完整密码与连接串")
    @ExcelProperty("错误摘要")
    private String errorMsg;

    @Schema(description = "是否走只读连接执行：0 否、1 是")
    @ExcelProperty("是否走只读连接执行")
    private Integer readonlyConnection;

    @Schema(description = "客户端 IP")
    @ExcelProperty("客户端 IP")
    private String clientIp;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
