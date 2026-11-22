package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.circuit.AiModelCircuitStatePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.circuit.AiModelCircuitStateRespVO;
import cn.iocoder.boot.module.data_query.entity.AiModelCircuitState;
import cn.iocoder.boot.module.data_query.framework.model.ModelCircuitBreaker;
import cn.iocoder.boot.module.data_query.service.AiModelCircuitStateService;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 模型熔断状态快照
 *
 * 运行时状态以 Redis 为准，本表只做审计 / 展示 / 重启恢复快照，故不开放新增与删除；
 * 唯一允许的写操作是 reset（人工止血时同时清 Redis 状态与 DB 快照）。
 */
@Tag(name = "管理后台 - 模型熔断状态")
@RestController
@RequestMapping("/data-query/ai-model-circuit-state")
@Validated
public class AiModelCircuitStateController {

    @Resource
    private AiModelCircuitStateService aiModelCircuitStateService;

    @Resource
    private ModelCircuitBreaker modelCircuitBreaker;

    @GetMapping("/get")
    @Operation(summary = "获得熔断状态快照")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-circuit-state:query')")
    public CommonResult<AiModelCircuitStateRespVO> get(@RequestParam("id") Long id) {
        AiModelCircuitState entity = aiModelCircuitStateService.getAiModelCircuitState(id);
        return success(BeanUtils.toBean(entity, AiModelCircuitStateRespVO.class));
    }

    @GetMapping("/get-by-key")
    @Operation(summary = "按熔断维度键读取实时状态（Redis 优先）")
    @Parameter(name = "circuitKey", description = "熔断维度键，如 1:2", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-circuit-state:query')")
    public CommonResult<AiModelCircuitStateRespVO> getByKey(@RequestParam("circuitKey") String circuitKey) {
        AiModelCircuitState snapshot = modelCircuitBreaker.snapshot(circuitKey);
        return success(BeanUtils.toBean(snapshot, AiModelCircuitStateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得熔断状态分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-circuit-state:query')")
    public CommonResult<PageResult<AiModelCircuitStateRespVO>> page(@Valid AiModelCircuitStatePageReqVO pageReqVO) {
        IPage<AiModelCircuitState> page = aiModelCircuitStateService.page(
                MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelCircuitState> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiModelCircuitStateRespVO.class));
    }

    @PutMapping("/reset")
    @Operation(summary = "人工重置熔断器为 CLOSED（同时清 Redis 与 DB 快照）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-circuit-state:update')")
    public CommonResult<Boolean> reset(@RequestParam("circuitKey") String circuitKey) {
        modelCircuitBreaker.reset(circuitKey);
        return success(true);
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出熔断状态 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-circuit-state:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportExcel(@Valid AiModelCircuitStatePageReqVO pageReqVO,
                            HttpServletResponse response) throws IOException {
        List<AiModelCircuitState> list = aiModelCircuitStateService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "模型熔断状态.xls", "数据", AiModelCircuitStateRespVO.class,
                BeanUtils.toBean(list, AiModelCircuitStateRespVO.class));
    }

    private LambdaQueryWrapper<AiModelCircuitState> buildQuery(AiModelCircuitStatePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelCircuitState>()
                .likeIfPresent(AiModelCircuitState::getCircuitKey, reqVO.getCircuitKey())
                .eqIfPresent(AiModelCircuitState::getState, reqVO.getState())
                .eqIfPresent(AiModelCircuitState::getModelConfigId, reqVO.getModelConfigId())
                .betweenIfPresent(AiModelCircuitState::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelCircuitState::getDeleted, 0)
                .orderByDesc(AiModelCircuitState::getUpdateTime);
    }

}
