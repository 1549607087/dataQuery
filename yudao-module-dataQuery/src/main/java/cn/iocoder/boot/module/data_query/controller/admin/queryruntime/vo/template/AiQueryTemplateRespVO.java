package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.template;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 查询模板 Response VO
 *
 * 不回显 tenantId / deleted / creator / updater 等内部字段；
 * 审计表内容可能含 SQL 文本，除上述内部字段外完整回显。
 */
@Schema(description = "管理后台 - 查询模板 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiQueryTemplateRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "模板名称")
    @ExcelProperty("模板名称")
    private String name;

    @Schema(description = "模板编码，租户内唯一，供程序引用")
    @ExcelProperty("模板编码")
    private String code;

    @Schema(description = "模板分类，如 销售 / 财务 / 运营")
    @ExcelProperty("模板分类")
    private String category;

    @Schema(description = "参数化问题模板文本，含占位符")
    @ExcelProperty("参数化问题模板文本")
    private String questionTemplate;

    @Schema(description = "sql_template 仅存储参数化模板文本，绝不在此执行；P4 校验器与只读执行器落地后才允许被引用")
    @ExcelProperty("sql_template 仅")
    private String sqlTemplate;

    @Schema(description = "参数定义 JSON：名称、类型、必填、默认值、校验规则")
    @ExcelProperty("参数定义 JSON")
    private String paramsDef;

    @Schema(description = "关联 ai_datasource.id，模板适用的数据源，0 表示通用")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "是否启用：0 否、1 是")
    @ExcelProperty("是否启用")
    private Integer enabled;

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
