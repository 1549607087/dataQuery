package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 查询审计（ai_query_audit）
 *
 * 问数运行时 4 张表（P2-B）全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_query_audit")
@Data
public class AiQueryAudit {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_query_session.id，独立提问可为 0
     */
    @TableField(value = "session_id")
    private Long sessionId;

    /**
     * 提问用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 关联 ai_datasource.id，本次问答使用的数据源
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 链路追踪 ID，贯穿会话、模型调用与本次审计
     */
    @TableField(value = "trace_id")
    private String traceId;

    /**
     * 用户原始问题文本
     */
    @TableField(value = "question")
    private String question;

    /**
     * 改写 / 归一化后的问题文本
     */
    @TableField(value = "normalized_question")
    private String normalizedQuestion;

    /**
     * 意图识别结果编码
     */
    @TableField(value = "intent_code")
    private String intentCode;

    /**
     * Schema 召回命中表清单，JSON 数组
     */
    @TableField(value = "recalled_tables")
    private String recalledTables;

    /**
     * 关联 ai_prompt_version.id，本次使用的提示词版本
     */
    @TableField(value = "prompt_version_id")
    private Long promptVersionId;

    /**
     * 关联 ai_model_config.id，本次使用的模型配置
     */
    @TableField(value = "model_config_id")
    private Long modelConfigId;

    /**
     * LLM 生成的原始 SQL
     */
    @TableField(value = "generated_sql")
    private String generatedSql;

    /**
     * 经静态校验 / 改写后的 SQL
     */
    @TableField(value = "validated_sql")
    private String validatedSql;

    /**
     * 最终下发只读连接执行的 SQL
     */
    @TableField(value = "executed_sql")
    private String executedSql;

    /**
     * SQL 校验状态：PASS / REJECT / REWRITE 等，由静态校验器写入
     */
    @TableField(value = "sql_status")
    private String sqlStatus;

    /**
     * 结果返回行数
     */
    @TableField(value = "result_rows")
    private Long resultRows;

    /**
     * 结果集是否被 max_rows 截断：0 否、1 是
     */
    @TableField(value = "result_truncated")
    private Integer resultTruncated;

    /**
     * 端到端耗时（毫秒）
     */
    @TableField(value = "latency_ms")
    private Integer latencyMs;

    /**
     * 结果状态：1 成功、2 澄清、3 拒绝、4 降级、5 失败、6 已停止（用户主动停止）
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 错误码，成功为空
     */
    @TableField(value = "error_code")
    private String errorCode;

    /**
     * 错误摘要，禁止记录完整密码与连接串
     */
    @TableField(value = "error_msg")
    private String errorMsg;

    /**
     * 是否走只读连接执行：0 否、1 是
     */
    @TableField(value = "readonly_connection")
    private Integer readonlyConnection;

    /**
     * 客户端 IP
     */
    @TableField(value = "client_ip")
    private String clientIp;

    /**
     * 租户私有，全表不登记 ignore-tables，写入显式 setTenantId
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * creator
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * create_time
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * updater
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * update_time
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * deleted
     */
    @TableField(value = "deleted")
    private Integer deleted;

}
