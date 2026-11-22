package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 数据源精简 Response VO（下拉框 / S0 上下文固化用，不含任何连接信息）
 */
@Schema(description = "管理后台 - 数据源精简 Response VO")
@Data
public class AiDatasourceSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "数据源显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "数据源编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "数据库类型")
    private Integer dsType;

    @Schema(description = "SQL 方言")
    private String dialect;

    @Schema(description = "状态：0 启用、1 停用、2 维护中")
    private Integer status;

    @Schema(description = "健康状态：0 未检测、1 健康、2 异常")
    private Integer healthStatus;

    @Schema(description = "是否强制只读连接")
    private Integer readonlyEnabled;

}
