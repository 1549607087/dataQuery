package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.template;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 查询模板新增/修改 Request VO
 *
 * tenant_id / creator / updater / create_time / update_time / deleted 一律由服务端写入，
 * 禁止前端传入；主键 id 为空表示新增。
 */
@Schema(description = "管理后台 - 查询模板新增/修改 Request VO")
@Data
public class AiQueryTemplateSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "模板名称", example = "")
    @NotEmpty(message = "Name不能为空")
    private String name;

    @Schema(description = "模板编码，租户内唯一，供程序引用", example = "")
    @NotEmpty(message = "Code不能为空")
    private String code;

    @Schema(description = "模板分类，如 销售 / 财务 / 运营", example = "")
    private String category;

    @Schema(description = "参数化问题模板文本，含占位符", example = "")
    private String questionTemplate;

    @Schema(description = "sql_template 仅存储参数化模板文本，绝不在此执行；P4 校验器与只读执行器落地后才允许被引用", example = "")
    private String sqlTemplate;

    @Schema(description = "参数定义 JSON：名称、类型、必填、默认值、校验规则", example = "")
    private String paramsDef;

    @Schema(description = "关联 ai_datasource.id，模板适用的数据源，0 表示通用", example = "")
    private Long datasourceId;

    @Schema(description = "是否启用：0 否、1 是", example = "")
    private Integer enabled;

    @Schema(description = "排序", example = "")
    private Integer sort;

    @Schema(description = "备注", example = "")
    private String remark;

}
