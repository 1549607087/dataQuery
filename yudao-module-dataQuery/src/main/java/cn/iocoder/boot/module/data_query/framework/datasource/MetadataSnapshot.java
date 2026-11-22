package cn.iocoder.boot.module.data_query.framework.datasource;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次元数据同步从目标库采集到的内存快照，不落任何业务数据行。
 *
 * 只含结构信息：表 / 列 / 主键 / 外键。sample_rows、sample_values 等业务数据
 * 一律不由同步器采集，也不允许进入向量索引。
 */
public final class MetadataSnapshot {

    private MetadataSnapshot() {
    }

    /** 表级快照 */
    @Data
    public static class TableInfo {
        private String schemaName;
        private String tableName;
        /** 1 物理表 / 2 视图，对应 ai_datasource_table.table_type */
        private Integer tableType;
        private String remark;
        private List<String> primaryKeys = new ArrayList<>();
        private List<ColumnInfo> columns = new ArrayList<>();
        private String structHash;
    }

    /** 列级快照 */
    @Data
    public static class ColumnInfo {
        private String columnName;
        /** 数据库原始类型描述，如 numeric(12,2) / character varying(64) */
        private String dataType;
        /** JDBC TYPE_NAME */
        private String jdbcType;
        private Integer columnSize;
        private Integer nullable;
        private Integer primaryKey;
        private Integer autoIncrement;
        private String defaultValue;
        private Integer ordinalPosition;
        private String remark;
        private String structHash;
    }

    /** 外键推断出的 Join 路径候选（默认不启用，需人工 verified） */
    @Data
    public static class RelationInfo {
        private String leftTableName;
        private String rightTableName;
        /** [{"left":"id","right":"customer_id"}] */
        private String joinPairs;
        private String fkName;
    }

}
