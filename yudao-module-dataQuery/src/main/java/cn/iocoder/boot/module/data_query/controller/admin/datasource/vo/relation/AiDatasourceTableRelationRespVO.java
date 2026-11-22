package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 表关系 / Join 路径 Response VO
 */
@Schema(description = "管理后台 - 表关系 / Join 路径 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiDatasourceTableRelationRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_datasource.id；Join 路径不跨数据源")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "左表，关联 ai_datasource_table.id")
    @ExcelProperty("左表")
    private Long leftTableId;

    @Schema(description = "右表，关联 ai_datasource_table.id")
    @ExcelProperty("右表")
    private Long rightTableId;

    @Schema(description = "左表物理名，冗余便于生成 SQL")
    @ExcelProperty("左表物理名")
    private String leftTableName;

    @Schema(description = "右表物理名，冗余便于生成 SQL")
    @ExcelProperty("右表物理名")
    private String rightTableName;

    @Schema(description = "连接类型：1 INNER、2 LEFT、3 RIGHT、4 FULL、5 CROSS")
    @ExcelProperty("连接类型")
    private Integer joinType;

    @Schema(description = "关联字段对 JSON 数组，如 [{\"left\":\"customer_id\",\"right\":\"id\"}]")
    @ExcelProperty("关联字段对 JSON 数组")
    private String joinPairs;

    @Schema(description = "额外 ON 条件表达式，必须经静态校验，禁止子查询与函数注入")
    @ExcelProperty("额外 ON 条件表达式")
    private String joinCondition;

    @Schema(description = "基数：1 一对一、2 一对多、3 多对一、4 多对多")
    @ExcelProperty("基数")
    private Integer cardinality;

    @Schema(description = "路径代价 / 优先级，数值越小越优先推荐给 LLM")
    @ExcelProperty("路径代价 / 优先级")
    private Integer cost;

    @Schema(description = "关系业务说明，如「订单归属客户」")
    @ExcelProperty("关系业务说明")
    private String description;

    @Schema(description = "来源：1 外键自动推断、2 手工配置、3 LLM 建议后人工审核")
    @ExcelProperty("来源")
    private Integer source;

    @Schema(description = "是否已用真实 SQL 验证可执行：0 否、1 是")
    @ExcelProperty("是否已用真实 SQL 验证可")
    private Integer verified;

    @Schema(description = "是否启用：0 否、1 是")
    @ExcelProperty("是否启用")
    private Integer enabled;

    @Schema(description = "状态：0 正常、1 停用")
    @ExcelProperty("状态")
    private Integer status;

    @Schema(description = "排序")
    @ExcelProperty("排序")
    private Integer sort;

    @Schema(description = "备注")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;


}
