package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库：租户私有的文档集合，独立问答旁路的顶层实体
 * @TableName ai_knowledge_base
 */
@TableName(value ="ai_knowledge_base")
@Data
public class AiKnowledgeBase {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 租户 ID，必须真实、禁止写 0；严禁登记 ignore-tables
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * 
     */
    @TableField(value = "name")
    private String name;

    /**
     * 业务编码，租户内唯一，供接口按 code 定位知识库
     */
    @TableField(value = "code")
    private String code;

    /**
     * 
     */
    @TableField(value = "description")
    private String description;

    /**
     * 0 正常 / 1 停用；停用后该库不参与检索
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 文档数冗余统计，上传/删除时在同事务内维护
     */
    @TableField(value = "document_count")
    private Integer documentCount;

    /**
     * 切片数冗余统计，重建时在同事务内维护
     */
    @TableField(value = "chunk_count")
    private Integer chunkCount;

    /**
     * 该库向量所用模型代号指纹（冗余自 chunk），格式 provider.code:model_id:dimension，
     * 如 openai:text-embedding-3-small:1536；由系统写入，便于展示与判断是否需要重建。
     * 注意：不是 ai_model_config.id，也不等于单独的 model_id。
     */
    @TableField(value = "embedding_model_code")
    private String embeddingModelCode;

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