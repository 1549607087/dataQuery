package cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.audit;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 查询审计分页 Request VO
 */
@Schema(description = "管理后台 - 查询审计分页 Request VO")
@Data
public class AiQueryAuditPageReqVO extends PageParam {

    @Schema(description = "关联 ai_query_session.id，独立提问可为 0")
    private Long sessionId;

    @Schema(description = "提问用户 ID")
    private Long userId;

    @Schema(description = "关联 ai_datasource.id，本次问答使用的数据源")
    private Long datasourceId;

    @Schema(description = "链路追踪 ID，贯穿会话、模型调用与本次审计")
    private String traceId;

    @Schema(description = "意图识别结果编码")
    private String intentCode;

    @Schema(description = "SQL 校验状态：PASS / REJECT / REWRITE 等，由静态校验器写入")
    private String sqlStatus;

    @Schema(description = "结果状态：1 成功、2 澄清、3 拒绝、4 降级、5 失败")
    private Integer status;

    @Schema(description = "是否走只读连接执行：0 否、1 是")
    private Integer readonlyConnection;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
