package cn.iocoder.boot.module.data_query.framework.importmeta;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 表结构一键导入可调参数。
 *
 * 这里只放「上限与默认值」，不放安全红线：租户归属校验、驱动白名单、
 * 系统 schema 排除、只读连接、S6 白名单交集全部沿用既有实现，不因一键导入而放宽。
 */
@Component
@ConfigurationProperties(prefix = "data-query.metadata-import")
@Data
public class MetadataImportProperties {

    /** 单次导入允许的最大表数，超出直接拒绝（防止用户没配白名单时误扫整库） */
    private Integer maxTables = 500;

    /** 预览响应中最多回传多少张表的明细；总数仍按真实值返回 */
    private Integer maxPreviewTables = 200;

    /** 每张预览表最多回传多少个字段名 */
    private Integer maxColumnsPerPreviewTable = 20;

    /** 一键创建数据源时的默认返回行数上限 */
    private Integer defaultMaxRows = 1000;

    /** 导入完成后是否默认构建向量索引；构建失败只告警、不回滚元数据 */
    private Boolean buildVectorByDefault = true;

    /** 未指定 dsType 时的默认数据库类型：2 PostgreSQL */
    private Integer defaultDsType = 2;

}
