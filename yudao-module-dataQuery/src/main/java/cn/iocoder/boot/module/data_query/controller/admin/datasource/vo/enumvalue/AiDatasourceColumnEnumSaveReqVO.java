package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.enumvalue;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 字段枚举映射新增/修改 Request VO
 *
 * 同步 / 检测 / 健康状态等运行时字段由服务端回写，不接受前端提交；
 * tenant_id 一律由服务端按当前租户上下文写入，禁止前端传入。
 */
@Schema(description = "管理后台 - 字段枚举映射新增/修改 Request VO")
@Data
public class AiDatasourceColumnEnumSaveReqVO {

    @Schema(description = "主键", example = "")
    private Long id;

    @Schema(description = "关联 ai_datasource.id", example = "")
    @NotNull(message = "DatasourceId不能为空")
    private Long datasourceId;

    @Schema(description = "关联 ai_datasource_table.id", example = "")
    private Long tableId;

    @Schema(description = "关联 ai_datasource_column.id", example = "")
    @NotNull(message = "ColumnId不能为空")
    private Long columnId;

    @Schema(description = "数据库中存储的原值", example = "")
    @NotEmpty(message = "SourceValue不能为空")
    private String sourceValue;

    @Schema(description = "业务显示名，拼入提示词让 LLM 理解「1 = 已支付」", example = "")
    private String displayName;

    @Schema(description = "关联 system_dict_type.dict_type，空表示纯本地映射", example = "")
    private String dictType;

    @Schema(description = "关联 system_dict_data.value", example = "")
    private String dictValue;

    @Schema(description = "该枚举值的业务说明", example = "")
    private String description;

    @Schema(description = "来源：1 采样自动识别、2 手工录入、3 数据字典同步", example = "")
    private Integer source;

    @Schema(description = "采样中该值出现次数，用于判断有效性", example = "")
    private Long sampleCount;

    @Schema(description = "状态：0 启用、1 停用", example = "")
    private Integer status;

    @Schema(description = "排序", example = "")
    private Integer sort;

    @Schema(description = "备注", example = "")
    private String remark;

}
