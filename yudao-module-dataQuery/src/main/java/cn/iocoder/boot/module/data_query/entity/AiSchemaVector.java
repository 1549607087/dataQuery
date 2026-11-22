package cn.iocoder.boot.module.data_query.entity;

import cn.iocoder.boot.module.data_query.framework.vector.PgVectorTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Schema 向量索引（ai_schema_vector，pgvector）
 *
 * 租户边界：本表是数据源元数据的派生物，继承「数据源侧全租户私有、无平台共享」口径。
 * 严禁登记 yudao.tenant.ignore-tables、严禁 @TenantIgnore；
 * 查询由 TenantDatabaseInterceptor 追加 tenant_id = 当前租户，写入必须显式 setTenantId
 * 且按 datasource_id 校验归属。
 *
 * embedding 用 String 承载 pgvector 字面量 "[0.1,0.2,...]"，必须经 PgVectorTypeHandler
 * 以 Types.OTHER 写入；NULL 表示「Document 已构建、等待 P2 的 Embedding 接线」。
 */
@TableName(value = "ai_schema_vector", autoResultMap = true)
@Data
public class AiSchemaVector {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(value = "tenant_id")
    private Long tenantId;

    @TableField(value = "datasource_id")
    private Long datasourceId;

    /** 1 表级 / 2 列级 / 3 术语口径级 */
    @TableField(value = "object_type")
    private Integer objectType;

    @TableField(value = "table_id")
    private Long tableId;

    @TableField(value = "column_id")
    private Long columnId;

    @TableField(value = "schema_name")
    private String schemaName;

    @TableField(value = "table_name")
    private String tableName;

    @TableField(value = "column_name")
    private String columnName;

    @TableField(value = "content")
    private String content;

    /** SHA-256(content + "|" + embedding_model_code)，未变则跳过重新 Embedding */
    @TableField(value = "content_hash")
    private String contentHash;

    /** pgvector 字面量；NULL = 待构建 */
    @TableField(value = "embedding", typeHandler = PgVectorTypeHandler.class)
    private String embedding;

    @TableField(value = "embedding_model_code")
    private String embeddingModelCode;

    @TableField(value = "embedding_dim")
    private Integer embeddingDim;

    @TableField(value = "struct_version")
    private Integer structVersion;

    @TableField(value = "visible")
    private Integer visible;

    @TableField(value = "queryable")
    private Integer queryable;

    @TableField(value = "sensitive_type")
    private Integer sensitiveType;

    @TableField(value = "mask_rule")
    private String maskRule;

    /** 0 待构建 / 1 已构建 / 2 需重建 / 4 已下线；4 不得被召回 */
    @TableField(value = "sync_status")
    private Integer syncStatus;

    /** 0 正常 / 1 停用 */
    @TableField(value = "status")
    private Integer status;

    @TableField(value = "creator")
    private String creator;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "updater")
    private String updater;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(value = "deleted")
    private Integer deleted;

}
