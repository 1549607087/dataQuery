package cn.iocoder.boot.module.data_query.controller.admin.sync.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 管理后台 - 元数据可见性批量维护 Request VO */
@Schema(description = "管理后台 - 元数据可见性批量维护 Request VO")
@Data
public class MetadataVisibilityReqVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源编号不能为空")
    private Long datasourceId;

    @Schema(description = "表元数据编号列表（可空）")
    private List<Long> tableIds;

    @Schema(description = "字段元数据编号列表（可空）")
    private List<Long> columnIds;

    @Schema(description = "是否可见：0 否、1 是；为空表示不修改")
    private Integer visible;

    @Schema(description = "是否允许 LLM 引用：0 否、1 是；为空表示不修改")
    private Integer queryable;

}
