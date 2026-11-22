package cn.iocoder.boot.module.data_query.framework.knowledge.recall;

import lombok.Data;

/**
 * 知识库切片召回命中项
 *
 * <p>承载两路召回（向量 / 关键词）的原始分数与排名，以及引用溯源四件套
 * （pageNo / headingPath / charStart / charEnd），供 RRF 融合、Token 裁剪与前端高亮使用。
 * 与 {@code SchemaVectorHit} 同范式，但针对 ai_knowledge_chunk 表。
 */
@Data
public class KnowledgeChunkHit {

    /** 切片主键 ai_knowledge_chunk.id */
    private Long id;

    private Long baseId;

    private Long documentId;

    private String fileName;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    /** 页码（PDF/DOCX 有，TXT/MD 为 null） */
    private Integer pageNo;

    /** 标题路径，如「Redis > 持久化」 */
    private String headingPath;

    /** 引用高亮：原文起始字符偏移 */
    private Integer charStart;

    /** 引用高亮：原文结束字符偏移 */
    private Integer charEnd;

    /** 逻辑文档标识（版本链），供引用溯源 */
    private String docKey;

    /** 命中文片所属文档版本号，供引用溯源与水位回溯标识 */
    private Integer version;

    /** 向量路余弦距离（越小越相关） */
    private Double distance;

    /** 关键词路 CASE 打分（越大越相关） */
    private Double keywordScore;

    private Integer vectorRank;

    private Integer keywordRank;

    /** 命中来源：VECTOR / KEYWORD / BOTH */
    private String source;

    /** RRF 融合分 */
    private Double rrfScore;

    /** Rerank 相关性分数（仅 rerank 生效时写入；未精排为 null） */
    private Double rerankScore;

    /** 裁剪后连续编号 1..K，对应回答中的 [n] */
    private Integer refNo;

    /** 归并键：切片主键天然唯一 */
    public String key() {
        return String.valueOf(id);
    }
}
