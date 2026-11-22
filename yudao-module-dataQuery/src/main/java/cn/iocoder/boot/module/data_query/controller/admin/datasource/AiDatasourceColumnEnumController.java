package cn.iocoder.boot.module.data_query.controller.admin.datasource;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.enumvalue.AiDatasourceColumnEnumPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.enumvalue.AiDatasourceColumnEnumRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.enumvalue.AiDatasourceColumnEnumSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumnEnum;
import cn.iocoder.boot.module.data_query.service.AiDatasourceColumnEnumService;
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
 * 管理后台 - 字段枚举映射
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId，
 * 并按 datasource_id 校验数据源归属，防止把元数据写到其它租户的数据源下。
 */
@Tag(name = "管理后台 - 字段枚举映射")
@RestController
@RequestMapping("/data-query/ai-datasource-column-enum")
@Validated
public class AiDatasourceColumnEnumController {

    @Resource
    private AiDatasourceColumnEnumService aiDatasourceColumnEnumService;

    @PostMapping("/create")
    @Operation(summary = "创建字段枚举映射")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:create')")
    public CommonResult<Long> createAiDatasourceColumnEnum(@Valid @RequestBody AiDatasourceColumnEnumSaveReqVO reqVO) {
        return success(aiDatasourceColumnEnumService.createAiDatasourceColumnEnum(BeanUtils.toBean(reqVO, AiDatasourceColumnEnum.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新字段枚举映射")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:update')")
    public CommonResult<Boolean> updateAiDatasourceColumnEnum(@Valid @RequestBody AiDatasourceColumnEnumSaveReqVO reqVO) {
        aiDatasourceColumnEnumService.updateAiDatasourceColumnEnum(BeanUtils.toBean(reqVO, AiDatasourceColumnEnum.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除字段枚举映射")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:delete')")
    public CommonResult<Boolean> deleteAiDatasourceColumnEnum(@RequestParam("id") Long id) {
        aiDatasourceColumnEnumService.deleteAiDatasourceColumnEnum(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除字段枚举映射")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:delete')")
    public CommonResult<Boolean> deleteAiDatasourceColumnEnumList(@RequestParam("ids") List<Long> ids) {
        aiDatasourceColumnEnumService.deleteAiDatasourceColumnEnumList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得字段枚举映射")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:query')")
    public CommonResult<AiDatasourceColumnEnumRespVO> getAiDatasourceColumnEnum(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiDatasourceColumnEnumService.getAiDatasourceColumnEnum(id), AiDatasourceColumnEnumRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得字段枚举映射分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:query')")
    public CommonResult<PageResult<AiDatasourceColumnEnumRespVO>> getAiDatasourceColumnEnumPage(@Valid AiDatasourceColumnEnumPageReqVO pageReqVO) {
        IPage<AiDatasourceColumnEnum> page = aiDatasourceColumnEnumService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiDatasourceColumnEnum> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiDatasourceColumnEnumRespVO.class));
    }

    @GetMapping("/list-by-datasource")
    @Operation(summary = "按数据源获得字段枚举映射列表（校验数据源归属）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:query')")
    public CommonResult<List<AiDatasourceColumnEnumRespVO>> getAiDatasourceColumnEnumListByDatasource(
            @RequestParam("datasourceId") Long datasourceId) {
        return success(BeanUtils.toBean(aiDatasourceColumnEnumService.listByDatasourceId(datasourceId), AiDatasourceColumnEnumRespVO.class));
    }

    @GetMapping("/list-by-column")
    @Operation(summary = "按字段获得启用的枚举映射")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:query')")
    public CommonResult<List<AiDatasourceColumnEnumRespVO>> getEnumListByColumn(
            @RequestParam("columnId") Long columnId) {
        return success(BeanUtils.toBean(aiDatasourceColumnEnumService.listEnabledByColumnId(columnId),
                AiDatasourceColumnEnumRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出字段枚举映射 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column-enum:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiDatasourceColumnEnumExcel(@Valid AiDatasourceColumnEnumPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiDatasourceColumnEnum> list = aiDatasourceColumnEnumService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "字段枚举映射.xls", "数据", AiDatasourceColumnEnumRespVO.class,
                BeanUtils.toBean(list, AiDatasourceColumnEnumRespVO.class));
    }

    private LambdaQueryWrapper<AiDatasourceColumnEnum> buildQuery(AiDatasourceColumnEnumPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiDatasourceColumnEnum>()
                .eqIfPresent(AiDatasourceColumnEnum::getDatasourceId, reqVO.getDatasourceId())
                .eqIfPresent(AiDatasourceColumnEnum::getTableId, reqVO.getTableId())
                .eqIfPresent(AiDatasourceColumnEnum::getColumnId, reqVO.getColumnId())
                .likeIfPresent(AiDatasourceColumnEnum::getSourceValue, reqVO.getSourceValue())
                .likeIfPresent(AiDatasourceColumnEnum::getDisplayName, reqVO.getDisplayName())
                .eqIfPresent(AiDatasourceColumnEnum::getDictType, reqVO.getDictType())
                .eqIfPresent(AiDatasourceColumnEnum::getStatus, reqVO.getStatus())
                .betweenIfPresent(AiDatasourceColumnEnum::getCreateTime, reqVO.getCreateTime())
                .eq(AiDatasourceColumnEnum::getDeleted, 0)
                .orderByAsc(AiDatasourceColumnEnum::getSort)
                .orderByDesc(AiDatasourceColumnEnum::getId);
    }
}
