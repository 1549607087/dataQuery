package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryAuditContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;

/**
 * S7 只读执行（P4）。
 *
 * 内部强制先走 S6 校验，只有拿到 SafeSql 才会打开目标数据源连接；
 * 模型 / 用户的原始文本永远不会直接进 JDBC。P5 编排器调用同一个 Service，
 * 不另开一条「绕过闸门」的执行路径。
 */
public interface ReadOnlyQueryService {

    QueryResult execute(Long datasourceId, String rawText, String question, SchemaContext schemaContext);

    /**
     * P5 编排器专用重载：额外把会话 / 提示词版本 / 模型配置 / 意图 / traceId 落进审计，
     * 做到「一条审计 = 一次问数可追溯」。
     *
     * 校验与执行逻辑与 4 参版本完全相同，只是审计字段更完整；
     * auditContext 为 null 时行为与 4 参版本一致。
     */
    QueryResult execute(Long datasourceId, String rawText, String question,
                        SchemaContext schemaContext, QueryAuditContext auditContext);

}
