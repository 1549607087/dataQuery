package cn.iocoder.boot.module.data_query.service;

import java.util.Collection;

/**
 * Schema 向量索引与元数据的联动入口。
 *
 * <p>表 / 字段 / 枚举等元数据落库后调用本接口，由它完成两件事：
 * <ol>
 *   <li><b>标脏</b>：把受影响的 {@code ai_schema_vector} 行置 {@code sync_status = 2}，
 *       下次构建时跳过 {@code content_hash} 相等判断直接重算；</li>
 *   <li><b>触发重建</b>：在事务提交后（无事务则立即）按 {@code datasource_id} 防抖，
 *       异步执行一次增量构建。</li>
 * </ol>
 *
 * <p>两件事互相独立：自动重建被配置关闭、或 Embedding 调用失败时，脏标依然保留，
 * 因此 {@code status()} 能看到积压、人工 rebuild 也能补齐，不会出现「改了描述却查不到痕迹」。
 *
 * <p><b>为什么新增 / 删除走 {@link #onStructureChanged} 而不标脏：</b>
 * 新增的记录在向量表里还没有对应行，标脏更新不到任何行；删除后该行本就会被构建侧的
 * {@code retireVectors} 下线。两者都只需要触发一次构建，用脏标反而会把整库行都强制重算。
 */
public interface SchemaVectorIndexSyncService {

    /**
     * 表的内容变化（表名 / 中文名 / 业务说明 / 默认时间字段）。
     *
     * @param tableIds 受影响的表编号，必须属于 datasourceId
     */
    void onTablesChanged(Long datasourceId, Collection<Long> tableIds);

    /**
     * 字段的内容变化（中文名 / 业务说明 / 指标口径 / 同义词 / 单位 / 枚举含义）。
     *
     * @param columnIds 受影响的字段编号
     */
    void onColumnsChanged(Long datasourceId, Collection<Long> columnIds);

    /**
     * 结构变化（新增 / 删除 / 结构同步完成）：不标脏，直接请求增量重建。
     */
    void onStructureChanged(Long datasourceId);

    /**
     * 只请求重建，不打标。调用方已自行标脏（如可见性批量修改）时使用。
     */
    void requestRebuild(Long datasourceId);

}
