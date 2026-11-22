package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库文档：原始文件与解析状态，逻辑外键 base_id -> ai_knowledge_base.id
 * @TableName ai_knowledge_document
 */
@TableName(value ="ai_knowledge_document")
@Data
public class AiKnowledgeDocument {

    /** 首个版本号 */
    public static final int VERSION_FIRST = 1;
    /** is_latest：当前生效版本 */
    public static final int LATEST_YES = 1;
    /** is_latest：历史版本 */
    public static final int LATEST_NO = 0;

    /**
     * 
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 租户 ID，必须等于 base_id 所属租户；禁止写 0；严禁登记 ignore-tables
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * 逻辑外键 -> ai_knowledge_base.id，写入时校验同租户归属
     */
    @TableField(value = "base_id")
    private Long baseId;

    /**
     * 逻辑文档标识：同 base 下相同 doc_key 视为同一文档的多个版本。
     * 缺省取规范化文件名，上传方可显式传入以合并改名文档。
     */
    @TableField(value = "doc_key")
    private String docKey;

    /**
     * 文档版本号，同 doc_key 内从 1 递增；版本隔离靠独立 document 行，不靠此列做唯一键
     */
    @TableField(value = "version")
    private Integer version;

    /**
     * 1 当前生效版本（参与默认检索）/ 0 历史版本；同 doc_key 内至多一条为 1
     */
    @TableField(value = "is_latest")
    private Integer isLatest;

    /**
     * 
     */
    @TableField(value = "file_name")
    private String fileName;

    /**
     * 扩展名：TXT / MD / PDF / DOC / DOCX，一期全支持
     */
    @TableField(value = "file_type")
    private String fileType;

    /**
     * 
     */
    @TableField(value = "file_size")
    private Long fileSize;

    /**
     * 文件存储位置（本地路径或对象存储 key），不含凭证
     */
    @TableField(value = "file_url")
    private String fileUrl;

    /**
     * 文件字节 SHA256，用于上传幂等与内容变更判定
     */
    @TableField(value = "content_hash")
    private String contentHash;

    /**
     * 0 待解析 / 1 解析中 / 2 已完成 / 3 失败 / 4 已下线；失败只降级，可重试
     */
    @TableField(value = "parse_status")
    private Integer parseStatus;

    /**
     * 解析失败原因摘要，便于前端展示与排障
     */
    @TableField(value = "parse_error")
    private String parseError;

    /**
     * 解析后生成的切片数
     */
    @TableField(value = "chunk_count")
    private Integer chunkCount;

    /**
     * 解析后纯文本字符数
     */
    @TableField(value = "char_count")
    private Long charCount;

    /**
     * 是否参与检索：1 是 / 0 否（停用不删数据，重建时跳过）
     */
    @TableField(value = "enabled")
    private Integer enabled;

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