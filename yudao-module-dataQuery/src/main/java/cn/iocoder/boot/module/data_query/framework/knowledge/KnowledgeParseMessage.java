package cn.iocoder.boot.module.data_query.framework.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文档解析 MQ 消息体。
 *
 * 注意：租户上下文不放在 body 里，而是走消息属性 addProperty("tenantId", ...)；
 * body 里的 tenantId 仅作消费端三方校验的冗余副本（属性 vs body vs 库），防止串租户。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeParseMessage implements Serializable {

    /** 文档主键（同时作为消息 Key 与事务回查依据） */
    private Long documentId;

    /** 所属知识库 */
    private Long baseId;

    /** 冗余租户号，供消费端与消息属性、库记录三方校验 */
    private Long tenantId;

    /** 文件存储位置（infra 文件服务的 url） */
    private String fileUrl;

    /** 扩展名（大写） */
    private String fileType;

    /**
     * 解析模式：FULL 全量重切（rebuild）/ INCREMENTAL 增量（新版本上传，按 content_hash 复用旧切片向量）
     */
    private String parseMode;

    /** 逻辑文档标识：同 base 同 doc_key 为同一文档的多个版本 */
    private String docKey;

    /** 本次摄取生成的文档版本号（旧版 is_latest 置 0 的判定依据） */
    private Integer version;

    /** 增量复用的参照版本 documentId；为 null 表示无上一版（首版或全量重建） */
    private Long previousDocumentId;
}
