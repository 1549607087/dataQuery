package cn.iocoder.boot.module.data_query.controller.admin.model.vo.circuit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 模型熔断状态 Response VO
 *
 * 注意：运行时状态以 Redis 为准，本 VO 展示的是持久化快照，可能有秒级延迟。
 */
@Schema(description = "管理后台 - 模型熔断状态 Response VO")
@Data
@ExcelIgnoreUnannotated
public class AiModelCircuitStateRespVO {

    @Schema(description = "编号")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "模型配置编号")
    @ExcelProperty("模型配置编号")
    private Long modelConfigId;

    @Schema(description = "熔断维度键")
    @ExcelProperty("熔断维度键")
    private String circuitKey;

    @Schema(description = "状态：CLOSED / OPEN / HALF_OPEN")
    @ExcelProperty("状态")
    private String state;

    @Schema(description = "窗口起始时间")
    @ExcelProperty("窗口起始")
    private LocalDateTime windowStartTime;

    @Schema(description = "窗口内总请求数")
    @ExcelProperty("窗口请求数")
    private Integer windowTotal;

    @Schema(description = "窗口内失败数")
    @ExcelProperty("窗口失败数")
    private Integer windowFailed;

    @Schema(description = "窗口内成功数")
    @ExcelProperty("窗口成功数")
    private Integer windowSucceeded;

    @Schema(description = "窗口失败率（百分比）")
    @ExcelProperty("失败率")
    private BigDecimal failureRate;

    @Schema(description = "连续失败次数")
    @ExcelProperty("连续失败")
    private Integer consecutiveFailures;

    @Schema(description = "连续成功次数")
    @ExcelProperty("连续成功")
    private Integer consecutiveSuccesses;

    @Schema(description = "最近失败时间")
    private LocalDateTime lastFailureTime;

    @Schema(description = "最近失败分类")
    private String lastFailureCategory;

    @Schema(description = "进入 OPEN 的时间")
    private LocalDateTime openedTime;

    @Schema(description = "OPEN 到期时间")
    @ExcelProperty("OPEN 到期")
    private LocalDateTime openUntil;

    @Schema(description = "HALF_OPEN 已发探测数")
    @ExcelProperty("已发探测数")
    private Integer halfOpenProbes;

    @Schema(description = "HALF_OPEN 探测成功数")
    @ExcelProperty("探测成功数")
    private Integer halfOpenSucceeded;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "更新时间")
    @ExcelProperty("更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
