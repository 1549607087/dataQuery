package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 表关系 / Join 路径新增/修改 Request VO
 *
 * 同步 / 检测 / 健康状态等运行时字段由服务端回写，不接受前端提交；
 * tenant_id 一律由服务端按当前租户上下文写入，禁止前端传入。
 */
@Schema(description = "管理后台 - 表关系 / Join 路径新增/修改 Request VO")
@Data
public class AiDatasourceTableRelationSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "关联 ai_datasource.id；Join 路径不跨数据源", example = "")
    @NotNull(message = "DatasourceId不能为空")
    private Long datasourceId;

    @Schema(description = "左表，关联 ai_datasource_table.id", example = "")
    @NotNull(message = "LeftTableId不能为空")
    private Long leftTableId;

    @Schema(description = "右表，关联 ai_datasource_table.id", example = "")
    @NotNull(message = "RightTableId不能为空")
    private Long rightTableId;

    @Schema(description = "左表物理名，冗余便于生成 SQL", example = "")
    private String leftTableName;

    @Schema(description = "右表物理名，冗余便于生成 SQL", example = "")
    private String rightTableName;

    @Schema(description = "连接类型：1 INNER、2 LEFT、3 RIGHT、4 FULL、5 CROSS", example = "")
    private Integer joinType;

    @Schema(description = "关联字段对 JSON 数组，如 [{\\\"left\\\":\\\"customer_id\\\",\\\"right\\\":\\\"id\\\"}]", example = "")
    private String joinPairs;

    @Schema(description = "额外 ON 条件表达式，必须经静态校验，禁止子查询与函数注入", example = "")
    private String joinCondition;

    @Schema(description = "基数：1 一对一、2 一对多、3 多对一、4 多对多", example = "")
    private Integer cardinality;

    @Schema(description = "路径代价 / 优先级，数值越小越优先推荐给 LLM", example = "")
    private Integer cost;

    @Schema(description = "关系业务说明，如「订单归属客户」", example = "")
    private String description;

    @Schema(description = "来源：1 外键自动推断、2 手工配置、3 LLM 建议后人工审核", example = "")
    private Integer source;

    @Schema(description = "是否已用真实 SQL 验证可执行：0 否、1 是", example = "")
    private Integer verified;

    @Schema(description = "是否启用：0 否、1 是", example = "")
    private Integer enabled;

    @Schema(description = "状态：0 正常、1 停用", example = "")
    private Integer status;

    @Schema(description = "排序", example = "")
    private Integer sort;

    @Schema(description = "备注", example = "")
    private String remark;

}
