package cn.iocoder.boot.module.data_query.controller.admin.queryruntime;

import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.audit.AiQueryAuditPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.audit.AiQueryAuditRespVO;
import cn.iocoder.boot.module.data_query.entity.AiQueryAudit;
import cn.iocoder.boot.module.data_query.service.AiQueryAuditService;
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
 * 管理后台 - 查询审计
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId。
 */
@Tag(name = "管理后台 - 查询审计")
@RestController
@RequestMapping("/data-query/ai-query-audit")
@Validated
public class AiQueryAuditController {

    @Resource
    private AiQueryAuditService aiQueryAuditService;

    @GetMapping("/get")
    @Operation(summary = "获得查询审计")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-audit:query')")
    public CommonResult<AiQueryAuditRespVO> getAiQueryAudit(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiQueryAuditService.getAiQueryAudit(id), AiQueryAuditRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得查询审计分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-audit:query')")
    public CommonResult<PageResult<AiQueryAuditRespVO>> getAiQueryAuditPage(@Valid AiQueryAuditPageReqVO pageReqVO) {
        IPage<AiQueryAudit> page = aiQueryAuditService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiQueryAudit> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiQueryAuditRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出查询审计 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-audit:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiQueryAuditExcel(@Valid AiQueryAuditPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiQueryAudit> list = aiQueryAuditService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "查询审计.xls", "数据", AiQueryAuditRespVO.class,
                BeanUtils.toBean(list, AiQueryAuditRespVO.class));
    }

    private LambdaQueryWrapper<AiQueryAudit> buildQuery(AiQueryAuditPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiQueryAudit>()
                .eqIfPresent(AiQueryAudit::getSessionId, reqVO.getSessionId())
                .eqIfPresent(AiQueryAudit::getUserId, reqVO.getUserId())
                .eqIfPresent(AiQueryAudit::getDatasourceId, reqVO.getDatasourceId())
                .eqIfPresent(AiQueryAudit::getTraceId, reqVO.getTraceId())
                .eqIfPresent(AiQueryAudit::getIntentCode, reqVO.getIntentCode())
                .eqIfPresent(AiQueryAudit::getSqlStatus, reqVO.getSqlStatus())
                .eqIfPresent(AiQueryAudit::getStatus, reqVO.getStatus())
                .eqIfPresent(AiQueryAudit::getReadonlyConnection, reqVO.getReadonlyConnection())
                .betweenIfPresent(AiQueryAudit::getCreateTime, reqVO.getCreateTime())
                .eq(AiQueryAudit::getDeleted, 0)
                .orderByDesc(AiQueryAudit::getId);
    }
}
