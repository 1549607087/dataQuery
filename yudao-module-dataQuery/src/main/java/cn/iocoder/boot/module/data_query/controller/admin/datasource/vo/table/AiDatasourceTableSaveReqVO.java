package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.table;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 数据源表元数据新增/修改 Request VO
 *
 * 同步 / 检测 / 健康状态等运行时字段由服务端回写，不接受前端提交；
 * tenant_id 一律由服务端按当前租户上下文写入，禁止前端传入。
 */
@Schema(description = "管理后台 - 数据源表元数据新增/修改 Request VO")
@Data
public class AiDatasourceTableSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "关联 ai_datasource.id", example = "")
    @NotNull(message = "DatasourceId不能为空")
    private Long datasourceId;

    @Schema(description = "所属 schema，冗余存储以支持同一数据源多 schema", example = "")
    private String schemaName;

    @Schema(description = "物理表名", example = "")
    @NotEmpty(message = "TableName不能为空")
    private String tableName;

    @Schema(description = "对象类型：1 表、2 视图、3 物化视图、4 外部表", example = "")
    private Integer tableType;

    @Schema(description = "业务中文名，喂给 LLM 的 Schema 描述", example = "")
    private String nameZh;

    @Schema(description = "业务描述与统计注意事项，Schema 召回时注入提示词", example = "")
    private String description;

    @Schema(description = "表角色：1 事实表、2 维表、3 桥表、4 字典/配置表、5 其它", example = "")
    private Integer tableRole;

    @Schema(description = "主键列名，JSON 数组", example = "")
    private String primaryKey;

    @Schema(description = "默认时间字段，用于「本月 / 近 7 天」等时间口径解析", example = "")
    private String defaultTimeColumn;

    @Schema(description = "管理端是否可见：0 否、1 是", example = "")
    private Integer visible;

    @Schema(description = "是否允许 LLM 生成 SQL 时引用：0 否、1 是；静态校验的表白名单", example = "")
    private Integer queryable;

    @Schema(description = "整表敏感级别：0 无、1 内部、2 敏感、3 高敏", example = "")
    private Integer sensitiveLevel;

    @Schema(description = "Schema 召回时的排序权重", example = "")
    private Integer sort;

    @Schema(description = "备注", example = "")
    private String remark;

}
