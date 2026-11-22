package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.enumvalue;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.time.LocalDateTime;

/**
 * 管理后台 - 字段枚举映射 Response VO
 */
@Schema(description = "管理后台 - 字段枚举映射 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiDatasourceColumnEnumRespVO {

    @Schema(description = "主键")
    @ExcelProperty("主键")
    private Long id;

    @Schema(description = "关联 ai_datasource.id")
    @ExcelProperty("关联 ai_datasour")
    private Long datasourceId;

    @Schema(description = "关联 ai_datasource_table.id")
    @ExcelProperty("关联 ai_datasour")
    private Long tableId;

    @Schema(description = "关联 ai_datasource_column.id")
    @ExcelProperty("关联 ai_datasour")
    private Long columnId;

    @Schema(description = "数据库中存储的原值")
    @ExcelProperty("数据库中存储的原值")
    private String sourceValue;

    @Schema(description = "业务显示名，拼入提示词让 LLM 理解「1 = 已支付」")
    @ExcelProperty("业务显示名")
    private String displayName;

    @Schema(description = "关联 system_dict_type.dict_type，空表示纯本地映射")
    @ExcelProperty("关联 system_dict")
    private String dictType;

    @Schema(description = "关联 system_dict_data.value")
    @ExcelProperty("关联 system_dict")
    private String dictValue;

    @Schema(description = "该枚举值的业务说明")
    @ExcelProperty("该枚举值的业务说明")
    private String description;

    @Schema(description = "来源：1 采样自动识别、2 手工录入、3 数据字典同步")
    @ExcelProperty("来源")
    private Integer source;

    @Schema(description = "采样中该值出现次数，用于判断有效性")
    @ExcelProperty("采样中该值出现次数")
    private Long sampleCount;

    @Schema(description = "状态：0 启用、1 停用")
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
