package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;

/**
 * S3 Schema 召回（P3）
 *
 * 把「归一化问题 + 数据源」变成 SchemaContext：DDL 片段、Join 路径、枚举、
 * 指标口径、命中记录，同时是 S6 的表列白名单。
 *
 * 安全边界在此建立：tenantId 只取服务端上下文；向量路与关键词路都带 tenant_id
 * 硬过滤；query vector 必须由当前租户自己的 Embedding 模型生成；召回结果再经
 * PG 元数据二次核验（visible / queryable / sync_status / forbid）。
 * 任何降级都不放宽上述条件，也不把全库 Schema 硬塞给模型。
 */
public interface SchemaRecallService {

    /**
     * 执行召回。
     *
     * @param datasourceId 数据源编号（必须属于当前租户）
     * @param question     用户问题；本方法内做轻量归一化后作为 Embedding 输入
     * @param topK         期望返回的表数量；null 时用配置默认值
     */
    SchemaContext recall(Long datasourceId, String question, Integer topK);

}
