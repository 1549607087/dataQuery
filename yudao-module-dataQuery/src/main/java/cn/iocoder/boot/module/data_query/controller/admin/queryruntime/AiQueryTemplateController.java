package cn.iocoder.boot.module.data_query.controller.admin.queryruntime;

import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.template.AiQueryTemplatePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.template.AiQueryTemplateRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.template.AiQueryTemplateSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiQueryTemplate;
import cn.iocoder.boot.module.data_query.service.AiQueryTemplateService;
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
 * 管理后台 - 查询模板
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId。
 */
@Tag(name = "管理后台 - 查询模板")
@RestController
@RequestMapping("/data-query/ai-query-template")
@Validated
public class AiQueryTemplateController {

    @Resource
    private AiQueryTemplateService aiQueryTemplateService;

    @PostMapping("/create")
    @Operation(summary = "创建查询模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:create')")
    public CommonResult<Long> createAiQueryTemplate(@Valid @RequestBody AiQueryTemplateSaveReqVO reqVO) {
        return success(aiQueryTemplateService.createAiQueryTemplate(BeanUtils.toBean(reqVO, AiQueryTemplate.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新查询模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:update')")
    public CommonResult<Boolean> updateAiQueryTemplate(@Valid @RequestBody AiQueryTemplateSaveReqVO reqVO) {
        aiQueryTemplateService.updateAiQueryTemplate(BeanUtils.toBean(reqVO, AiQueryTemplate.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除查询模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:delete')")
    public CommonResult<Boolean> deleteAiQueryTemplate(@RequestParam("id") Long id) {
        aiQueryTemplateService.deleteAiQueryTemplate(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除查询模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:delete')")
    public CommonResult<Boolean> deleteAiQueryTemplateList(@RequestParam("ids") List<Long> ids) {
        aiQueryTemplateService.deleteAiQueryTemplateList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得查询模板")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:query')")
    public CommonResult<AiQueryTemplateRespVO> getAiQueryTemplate(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiQueryTemplateService.getAiQueryTemplate(id), AiQueryTemplateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得查询模板分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:query')")
    public CommonResult<PageResult<AiQueryTemplateRespVO>> getAiQueryTemplatePage(@Valid AiQueryTemplatePageReqVO pageReqVO) {
        IPage<AiQueryTemplate> page = aiQueryTemplateService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiQueryTemplate> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiQueryTemplateRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出查询模板 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-template:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiQueryTemplateExcel(@Valid AiQueryTemplatePageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiQueryTemplate> list = aiQueryTemplateService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "查询模板.xls", "数据", AiQueryTemplateRespVO.class,
                BeanUtils.toBean(list, AiQueryTemplateRespVO.class));
    }

    private LambdaQueryWrapper<AiQueryTemplate> buildQuery(AiQueryTemplatePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiQueryTemplate>()
                .likeIfPresent(AiQueryTemplate::getName, reqVO.getName())
                .eqIfPresent(AiQueryTemplate::getCode, reqVO.getCode())
                .eqIfPresent(AiQueryTemplate::getCategory, reqVO.getCategory())
                .eqIfPresent(AiQueryTemplate::getDatasourceId, reqVO.getDatasourceId())
                .eqIfPresent(AiQueryTemplate::getEnabled, reqVO.getEnabled())
                .betweenIfPresent(AiQueryTemplate::getCreateTime, reqVO.getCreateTime())
                .eq(AiQueryTemplate::getDeleted, 0)
                .orderByAsc(AiQueryTemplate::getSort)
                .orderByDesc(AiQueryTemplate::getId);
    }
}
