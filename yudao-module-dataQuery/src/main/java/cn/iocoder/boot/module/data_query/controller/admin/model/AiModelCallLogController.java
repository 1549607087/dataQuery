package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.log.AiModelCallLogPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.log.AiModelCallLogRespVO;
import cn.iocoder.boot.module.data_query.entity.AiModelCallLog;
import cn.iocoder.boot.module.data_query.service.AiModelCallLogService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - AI 模型调用日志（用量与成本）
 *
 * 日志由 LLM 编排引擎写入，管理端只提供查询与导出，不开放增删改接口。
 */
@Tag(name = "管理后台 - AI 模型调用日志")
@RestController
@RequestMapping("/data-query/ai-model-call-log")
@Validated
public class AiModelCallLogController {

    @Resource
    private AiModelCallLogService aiModelCallLogService;

    @GetMapping("/get")
    @Operation(summary = "获得模型调用日志")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-call-log:query')")
    public CommonResult<AiModelCallLogRespVO> getAiModelCallLog(@RequestParam("id") Long id) {
        AiModelCallLog callLog = aiModelCallLogService.getAiModelCallLog(id);
        return success(BeanUtils.toBean(callLog, AiModelCallLogRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得模型调用日志分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-call-log:query')")
    public CommonResult<PageResult<AiModelCallLogRespVO>> getAiModelCallLogPage(@Valid AiModelCallLogPageReqVO pageReqVO) {
        IPage<AiModelCallLog> page = aiModelCallLogService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelCallLog> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiModelCallLogRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出模型调用日志 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-call-log:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiModelCallLogExcel(@Valid AiModelCallLogPageReqVO pageReqVO,
                                          HttpServletResponse response) throws IOException {
        List<AiModelCallLog> list = aiModelCallLogService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "模型调用日志.xls", "数据", AiModelCallLogRespVO.class,
                BeanUtils.toBean(list, AiModelCallLogRespVO.class));
    }

    private LambdaQueryWrapper<AiModelCallLog> buildQuery(AiModelCallLogPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelCallLog>()
                .eqIfPresent(AiModelCallLog::getTraceId, reqVO.getTraceId())
                .eqIfPresent(AiModelCallLog::getConversationId, reqVO.getConversationId())
                .eqIfPresent(AiModelCallLog::getSceneCode, reqVO.getSceneCode())
                .eqIfPresent(AiModelCallLog::getModelConfigId, reqVO.getModelConfigId())
                .eqIfPresent(AiModelCallLog::getModelId, reqVO.getModelId())
                .eqIfPresent(AiModelCallLog::getStatus, reqVO.getStatus())
                .eqIfPresent(AiModelCallLog::getUserId, reqVO.getUserId())
                .betweenIfPresent(AiModelCallLog::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelCallLog::getDeleted, 0)
                .orderByDesc(AiModelCallLog::getId);
    }

}
