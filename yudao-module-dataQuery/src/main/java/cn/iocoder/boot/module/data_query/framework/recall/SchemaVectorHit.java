package cn.iocoder.boot.module.data_query.framework.recall;

import lombok.Data;

/**
 * ai_schema_vector 检索结果行（向量路 / 关键词路共用）。
 *
 * 只是 Mapper 的结果载体，不是表实体：distance 与 keywordScore 是计算列，
 * 且刻意不读取 embedding 字面量（1536 维，检索阶段无需回传应用层）。
 */
@Data
public class SchemaVectorHit {

    private Long id;
    private Integer objectType;
    private Long tableId;
    private Long columnId;
    private String schemaName;
    private String tableName;
    private String columnName;
    private String content;
    private Integer structVersion;

    /** 向量路：<=> 余弦距离，越小越相似 */
    private Double distance;

    /** 关键词路：ILIKE 命中加权分，越大越相关 */
    private Double keywordScore;

    /** 唯一键：同一对象可能同时被两路命中，RRF 融合时按此归并 */
    public String key() {
        return objectType + ":" + (tableId == null ? 0 : tableId)
                + ":" + (columnId == null ? 0 : columnId);
    }

}
