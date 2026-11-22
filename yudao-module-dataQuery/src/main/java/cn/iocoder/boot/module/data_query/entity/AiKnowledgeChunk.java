package cn.iocoder.boot.module.data_query.entity;

import cn.iocoder.boot.module.data_query.framework.vector.PgVectorTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库切片：带向量与引用定位信息，pgvector 检索的最小单位，租户私有。
 *
 * embedding 用 String 承载 pgvector 字面量 "[0.1,0.2,...]"，必须经 PgVectorTypeHandler
 * 以 Types.OTHER 写入（与 AiSchemaVector 同范式）；autoResultMap=true 让 MyBatis-Plus
 * 的 insert/updateById 走字段上的 typeHandler，否则会当成普通 varchar 导致 PG 报类型转换错。
 *
 * @TableName ai_knowledge_chunk
 */
@TableName(value = "ai_knowledge_chunk", autoResultMap = true)
@Data
public class AiKnowledgeChunk {

    /** is_latest：当前生效版本的切片 */
    public static final int LATEST_YES = 1;
    /** is_latest：历史版本的切片 */
    public static final int LATEST_NO = 0;

    /**
     * 
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 租户 ID，必须等于 document_id / base_id 所属租户；禁止写 0；严禁登记 ignore-tables
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * 冗余自 document，检索时与 tenant_id 一起硬过滤，免去 join
     */
    @TableField(value = "base_id")
    private Long baseId;

    /**
     * 逻辑外键 -> ai_knowledge_document.id
     */
    @TableField(value = "document_id")
    private Long documentId;

    /**
     * 冗余文档逻辑标识，检索按版本水位过滤用，免去 join document
     */
    @TableField(value = "doc_key")
    private String docKey;

    /**
     * 冗余文档版本号，随召回结果返回供引用溯源
     */
    @TableField(value = "version")
    private Integer version;

    /**
     * 冗余生效标记：1 当前版本的切片（默认检索）/ 0 历史版本切片
     */
    @TableField(value = "is_latest")
    private Integer isLatest;

    /**
     * 冗余自 document，供召回结果直接展示引用来源
     */
    @TableField(value = "file_name")
    private String fileName;

    /**
     * 
     */
    @TableField(value = "chunk_index")
    private Integer chunkIndex;

    /**
     * 切片正文，按「结构优先 + 500token 滑窗/overlap 50」切分
     */
    @TableField(value = "content")
    private String content;

    /**
     * SHA256(content + "|" + embedding_model_code)，换模型自动触发重建
     */
    @TableField(value = "content_hash")
    private String contentHash;

    /**
     * 
     */
    @TableField(value = "token_count")
    private Integer tokenCount;

    /**
     * 1536 维向量（pgvector 字面量）；换模型或换维度必须全量重建该库向量与索引，不允许混存
     */
    @TableField(value = "embedding", typeHandler = PgVectorTypeHandler.class)
    private String embedding;

    /**
     * 模型代号指纹，由当前租户 EMBEDDING 配置拼成：provider.code:model_id:dimension，
     * 如 openai:text-embedding-3-small:1536；参与唯一键与 content_hash，
     * 写入与查询必须同租户同模型。注意：不是 ai_model_config.id，也不等于单独的 model_id。
     */
    @TableField(value = "embedding_model_code")
    private String embeddingModelCode;

    /**
     * 向量维度，与 vector(1536) 列定义一致，写入时校验
     */
    @TableField(value = "embedding_dim")
    private Integer embeddingDim;

    /**
     * 0 待构建 / 1 已构建 / 2 需重建 / 4 已下线；4 不得被召回
     */
    @TableField(value = "sync_status")
    private Integer syncStatus;

    /**
     * PDF 页码（从 1 开始）；TXT/MD 为 NULL；引用溯源用
     */
    @TableField(value = "page_no")
    private Integer pageNo;

    /**
     * 标题层级路径，如 "2.1 报销标准 > 交通费"；引用溯源用
     */
    @TableField(value = "heading_path")
    private String headingPath;

    /**
     * 切片在解析后全文中的起始字符下标，前端高亮原文用
     */
    @TableField(value = "char_start")
    private Integer charStart;

    /**
     * 切片在解析后全文中的结束字符下标（不含），前端高亮原文用
     */
    @TableField(value = "char_end")
    private Integer charEnd;

    /**
     * 0 正常 / 1 停用；停用不物理删除，重建时保留历史
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * 
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * 
     */
    @TableField(value = "deleted")
    private Integer deleted;
}