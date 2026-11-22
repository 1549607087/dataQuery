package cn.iocoder.boot.module.data_query.framework.vector;

import lombok.Data;

/**
 * Schema 向量索引的一条「待写入文档」，由元数据派生，不含任何业务数据。
 *
 * objectType：1 表级 / 2 列级 / 3 术语口径级（一期只产 1、2，3 预留）。
 * content 允许进入的内容：表/列物理名、name_zh、description、metric_def、synonyms、
 * 单位、枚举 display_name（脱敏摘要）。严禁放 sample_rows 原始行、连接串、密码。
 * mask_rule = forbid 的列不生成 Document。
 */
@Data
public class SchemaDocument {

    private Integer objectType;
    private Long tableId;
    private Long columnId;
    private String schemaName;
    private String tableName;
    private String columnName;
    private String content;
    private String contentHash;
    private Integer visible;
    private Integer queryable;
    private Integer sensitiveType;
    private String maskRule;
    private Integer structVersion;

    /** 幂等键：与 uk_ai_schema_vector_01 的逻辑一致（不含 embedding_model_code，由调用方拼接） */
    public String key() {
        return objectType + ":" + (tableId == null ? 0 : tableId)
                + ":" + (columnId == null ? 0 : columnId);
    }

}
