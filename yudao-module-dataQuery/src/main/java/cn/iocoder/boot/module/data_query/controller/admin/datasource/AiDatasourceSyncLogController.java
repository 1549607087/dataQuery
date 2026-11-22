package cn.iocoder.boot.module.data_query.controller.admin.datasource;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.synclog.AiDatasourceSyncLogPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.synclog.AiDatasourceSyncLogRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceSyncLog;
import cn.iocoder.boot.module.data_query.service.AiDatasourceSyncLogService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 数据源同步日志
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId，
 * 并按 datasource_id 校验数据源归属，防止把元数据写到其它租户的数据源下。
 */
@Tag(name = "管理后台 - 数据源同步日志")
@RestController
@RequestMapping("/data-query/ai-datasource-sync-log")
@Validated
public class AiDatasourceSyncLogController {

    @Resource
    private AiDatasourceSyncLogService aiDatasourceSyncLogService;

    @GetMapping("/get")
    @Operation(summary = "获得数据源同步日志")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-sync-log:query')")
    public CommonResult<AiDatasourceSyncLogRespVO> getAiDatasourceSyncLog(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiDatasourceSyncLogService.getAiDatasourceSyncLog(id), AiDatasourceSyncLogRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源同步日志分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-sync-log:query')")
    public CommonResult<PageResult<AiDatasourceSyncLogRespVO>> getAiDatasourceSyncLogPage(@Valid AiDatasourceSyncLogPageReqVO pageReqVO) {
        IPage<AiDatasourceSyncLog> page = aiDatasourceSyncLogService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiDatasourceSyncLog> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiDatasourceSyncLogRespVO.class));
    }

    @GetMapping("/list-by-datasource")
    @Operation(summary = "按数据源获得数据源同步日志列表（校验数据源归属）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-sync-log:query')")
    public CommonResult<List<AiDatasourceSyncLogRespVO>> getAiDatasourceSyncLogListByDatasource(
            @RequestParam("datasourceId") Long datasourceId) {
        return success(BeanUtils.toBean(aiDatasourceSyncLogService.listByDatasourceId(datasourceId), AiDatasourceSyncLogRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出数据源同步日志 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-sync-log:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiDatasourceSyncLogExcel(@Valid AiDatasourceSyncLogPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiDatasourceSyncLog> list = aiDatasourceSyncLogService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "数据源同步日志.xls", "数据", AiDatasourceSyncLogRespVO.class,
                BeanUtils.toBean(list, AiDatasourceSyncLogRespVO.class));
    }

    private LambdaQueryWrapper<AiDatasourceSyncLog> buildQuery(AiDatasourceSyncLogPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiDatasourceSyncLog>()
                .eqIfPresent(AiDatasourceSyncLog::getDatasourceId, reqVO.getDatasourceId())
                .likeIfPresent(AiDatasourceSyncLog::getTaskNo, reqVO.getTaskNo())
                .eqIfPresent(AiDatasourceSyncLog::getSyncType, reqVO.getSyncType())
                .eqIfPresent(AiDatasourceSyncLog::getTriggerType, reqVO.getTriggerType())
                .eqIfPresent(AiDatasourceSyncLog::getStatus, reqVO.getStatus())
                .betweenIfPresent(AiDatasourceSyncLog::getCreateTime, reqVO.getCreateTime())
                .eq(AiDatasourceSyncLog::getDeleted, 0)
                .orderByDesc(AiDatasourceSyncLog::getId);
    }
}
