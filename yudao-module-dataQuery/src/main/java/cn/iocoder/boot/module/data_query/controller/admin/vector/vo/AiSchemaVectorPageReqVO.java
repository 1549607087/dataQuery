package cn.iocoder.boot.module.data_query.controller.admin.vector.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理后台 - Schema 向量分页 Request VO（必须带数据源编号以校验归属） */
@Schema(description = "管理后台 - Schema 向量分页 Request VO")
@Data
public class AiSchemaVectorPageReqVO extends PageParam {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据源编号不能为空")
    private Long datasourceId;

    @Schema(description = "对象类型：1 表级 / 2 列级 / 3 术语级")
    private Integer objectType;

    @Schema(description = "同步状态：0 待构建 / 1 已构建 / 2 需重建 / 4 已下线")
    private Integer syncStatus;

    @Schema(description = "表名 / 列名 / 向量化文本模糊匹配")
    private String keyword;

}
