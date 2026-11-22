package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SqlGuardResult;

/**
 * S6 SQL 抽取与静态校验（安全闸门，独立于模型）。
 *
 * 唯一入口：传入数据源编号 + 模型/用户原始文本 + S3 召回白名单，返回结构化报告。
 * 校验失败时报告里**不会**出现 executableSql，S7 因此无法执行被拒的 SQL。
 *
 * schemaContext 可以为空，此时必须给 question，由服务端自行调用 S3 召回（禁止前端
 * 直接传 schema 文本 / 表名绕过召回）。无论哪种方式，白名单都会再与 PG 元数据
 * 的 visible / queryable / forbid 取交集，前端伪造更大的 ID 集合也只能收窄、不能放宽。
 */
public interface SqlGuardService {

    /**
     * @param datasourceId   数据源编号（必须属于当前租户）
     * @param rawText        模型原始文本或手工编辑的 SQL（支持 markdown 代码块）
     * @param question       用户问题；schemaContext 为空且此值非空时服务端自动召回
     * @param schemaContext  S3 召回结果（P5 编排器直接传入，避免二次召回）
     */
    SqlGuardResult validate(Long datasourceId, String rawText, String question, SchemaContext schemaContext);

}
