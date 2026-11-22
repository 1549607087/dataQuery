package cn.iocoder.boot.module.data_query.framework.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 元数据变更后自动重建 Schema 向量索引的可调参数。
 *
 * <p>只决定「什么时候重建」，不改变任何安全边界：重建仍走
 * {@code SchemaVectorBuildService#build}，逐个经 {@code AiDatasourceOwnerValidator} 校验租户归属，
 * 增量口径由 {@code content_hash} 决定，可见性 / 越权列的过滤规则不可通过本配置放宽。
 */
@Component
@ConfigurationProperties(prefix = "data-query.vector")
@Data
public class SchemaVectorRebuildProperties {

    /**
     * 元数据变更后是否自动触发增量重建（默认开启）。
     *
     * <p>关闭后写接口仍会打 {@code sync_status = 2} 脏标，但不再自动构建，
     * 需要人工调 {@code POST /data-query/schema-vector/sync}；
     * {@code status()} 的待重建行数会把积压量暴露出来，不会静默漂移。
     */
    private Boolean autoRebuildEnabled = true;

    /**
     * 防抖窗口（毫秒）：窗口内同一数据源的多次变更合并成一次构建。
     *
     * <p>管理后台批量维护描述时是「逐字段保存」，若每次 update 都直接构建，
     * 改 30 个字段就是 30 次 Embedding 调用；合并后只按最终 content_hash 算一次。
     */
    private Integer debounceMs = 2000;

}
