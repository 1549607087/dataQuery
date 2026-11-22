package cn.iocoder.boot.module.data_query.framework.sqlguard;

import lombok.Data;

/**
 * P5 编排器传给 S7 的审计补充上下文（可选）。
 *
 * P4 的 4 参 execute 完全不变；ask 走 5 参重载，把会话 / 提示词版本 / 模型配置 /
 * 意图 / 链路 traceId 一并落进 ai_query_audit，做到「一条审计 = 一次问数可追溯」。
 * 为 null 时行为与 P4 完全一致（traceId 内部生成、sessionId=0），不改变既有回归口径。
 */
@Data
public class QueryAuditContext {

    /** 会话编号；独立提问为 0 */
    private Long sessionId;

    /** 链路追踪 ID；为空时由 S7 内部生成 */
    private String traceId;

    /** 提示词版本快照编号 */
    private Long promptVersionId;

    /** 模型配置编号 */
    private Long modelConfigId;

    /** 意图编码 */
    private String intentCode;

    /** 规范化后的问题（为空时回退为原始问题） */
    private String normalizedQuestion;
}
