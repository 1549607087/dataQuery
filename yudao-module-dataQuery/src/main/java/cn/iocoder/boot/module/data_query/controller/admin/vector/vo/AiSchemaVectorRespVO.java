package cn.iocoder.boot.module.data_query.controller.admin.vector.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - Schema 向量 Response VO
 *
 * 刻意不返回 embedding 向量字面量（1536 维、体积大且无展示价值），
 * 也不返回 tenant_id（避免回显租户信息）；只给构建状态与内容预览。
 */
@Schema(description = "管理后台 - Schema 向量 Response VO")
@Data
public class AiSchemaVectorRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "数据源编号")
    private Long datasourceId;

    @Schema(description = "对象类型：1 表级 / 2 列级 / 3 术语级")
    private Integer objectType;

    @Schema(description = "表 / 列元数据编号")
    private Long tableId;
    private Long columnId;

    @Schema(description = "物理名称")
    private String schemaName;
    private String tableName;
    private String columnName;

    @Schema(description = "向量化文本预览（不含业务数据行）")
    private String contentPreview;

    @Schema(description = "内容哈希")
    private String contentHash;

    @Schema(description = "Embedding 模型代号 / 维度")
    private String embeddingModelCode;
    private Integer embeddingDim;

    @Schema(description = "是否已生成向量")
    private Boolean embedded;

    @Schema(description = "结构版本号 / 可见 / 可查 / 敏感级别 / 脱敏规则")
    private Integer structVersion;
    private Integer visible;
    private Integer queryable;
    private Integer sensitiveType;
    private String maskRule;

    @Schema(description = "同步状态：0 待构建 / 1 已构建 / 2 需重建 / 4 已下线")
    private Integer syncStatus;

}
