package cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceTestRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorBuildRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台 - 表结构一键导入结果 Response VO
 *
 * 执行期的连接 / 同步 / 向量失败以结构化报告返回（HTTP 200 + success=false），
 * 并明确标注失败阶段与已创建的数据源编号，便于前端直接展示并一键重试；
 * 参数非法、编码冲突与跨租户访问仍走标准业务异常。
 */
@Schema(description = "管理后台 - 表结构一键导入结果 Response VO")
@Data
public class MetadataImportRespVO {

    @Schema(description = "整条链路是否成功（向量为非阻断告警，不影响本值）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    @Schema(description = "终态阶段：PREVIEW / DATASOURCE / CONNECT / SYNC / DONE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stage;

    @Schema(description = "是否只做了预览（无任何落库副作用）")
    private Boolean dryRun;

    @Schema(description = "数据源编号；失败时若已创建也返回，便于带 datasourceId 重试")
    private Long datasourceId;

    @Schema(description = "本次是否新建了数据源")
    private Boolean datasourceCreated;

    @Schema(description = "本次是否复用了已有数据源（幂等）")
    private Boolean datasourceReused;

    @Schema(description = "只读扫描预览结果")
    private MetadataPreviewRespVO preview;

    @Schema(description = "连通性检测结果")
    private AiDatasourceTestRespVO connection;

    @Schema(description = "全量元数据同步结果")
    private MetadataSyncRespVO sync;

    @Schema(description = "向量索引构建结果；未构建时为 null")
    private SchemaVectorBuildRespVO vector;

    @Schema(description = "分步执行记录，便于定位失败环节")
    private List<Step> steps = new ArrayList<>();

    @Schema(description = "非阻断告警（含未配置只读账号、Embedding 未接线等）")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "失败阶段的业务错误码；成功时为 null")
    private Long errorCode;

    @Schema(description = "失败原因（连接串与密码已脱敏）")
    private String errorMsg;

    @Schema(description = "可直接展示给用户的中文结果摘要")
    private String summary;

    @Schema(description = "整条链路耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "管理后台 - 导入步骤")
    @Data
    public static class Step {

        @Schema(description = "步骤名：PREVIEW / DATASOURCE / CONNECT / SYNC / VECTOR")
        private String name;

        @Schema(description = "SUCCESS / SKIPPED / WARN / FAILED")
        private String status;

        @Schema(description = "步骤说明（不含凭证）")
        private String detail;

        @Schema(description = "步骤耗时（毫秒）")
        private Long latencyMs;

    }

}
