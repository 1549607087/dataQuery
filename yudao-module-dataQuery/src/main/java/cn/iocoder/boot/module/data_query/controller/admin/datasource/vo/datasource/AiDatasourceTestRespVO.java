package cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 数据源连通性检测结果 Response VO
 */
@Schema(description = "管理后台 - 数据源连通性检测结果 Response VO")
@Data
public class AiDatasourceTestRespVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long datasourceId;

    @Schema(description = "是否连通", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    @Schema(description = "回写后的健康状态：1 健康、2 异常")
    private Integer healthStatus;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "结果说明；失败时为错误摘要，不含连接串与密码")
    private String message;

    @Schema(description = "检测时间")
    private LocalDateTime checkedAt;

}
