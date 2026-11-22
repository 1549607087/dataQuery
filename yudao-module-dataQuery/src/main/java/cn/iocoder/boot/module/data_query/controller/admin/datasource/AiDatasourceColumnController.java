package cn.iocoder.boot.module.data_query.controller.admin.datasource;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.column.AiDatasourceColumnPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.column.AiDatasourceColumnRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.column.AiDatasourceColumnSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceColumn;
import cn.iocoder.boot.module.data_query.service.AiDatasourceColumnService;
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
 * 管理后台 - 数据源字段元数据
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId，
 * 并按 datasource_id 校验数据源归属，防止把元数据写到其它租户的数据源下。
 */
@Tag(name = "管理后台 - 数据源字段元数据")
@RestController
@RequestMapping("/data-query/ai-datasource-column")
@Validated
public class AiDatasourceColumnController {

    @Resource
    private AiDatasourceColumnService aiDatasourceColumnService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源字段元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:create')")
    public CommonResult<Long> createAiDatasourceColumn(@Valid @RequestBody AiDatasourceColumnSaveReqVO reqVO) {
        return success(aiDatasourceColumnService.createAiDatasourceColumn(BeanUtils.toBean(reqVO, AiDatasourceColumn.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源字段元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:update')")
    public CommonResult<Boolean> updateAiDatasourceColumn(@Valid @RequestBody AiDatasourceColumnSaveReqVO reqVO) {
        aiDatasourceColumnService.updateAiDatasourceColumn(BeanUtils.toBean(reqVO, AiDatasourceColumn.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源字段元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:delete')")
    public CommonResult<Boolean> deleteAiDatasourceColumn(@RequestParam("id") Long id) {
        aiDatasourceColumnService.deleteAiDatasourceColumn(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除数据源字段元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:delete')")
    public CommonResult<Boolean> deleteAiDatasourceColumnList(@RequestParam("ids") List<Long> ids) {
        aiDatasourceColumnService.deleteAiDatasourceColumnList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源字段元数据")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:query')")
    public CommonResult<AiDatasourceColumnRespVO> getAiDatasourceColumn(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiDatasourceColumnService.getAiDatasourceColumn(id), AiDatasourceColumnRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源字段元数据分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:query')")
    public CommonResult<PageResult<AiDatasourceColumnRespVO>> getAiDatasourceColumnPage(@Valid AiDatasourceColumnPageReqVO pageReqVO) {
        IPage<AiDatasourceColumn> page = aiDatasourceColumnService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiDatasourceColumn> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiDatasourceColumnRespVO.class));
    }

    @GetMapping("/list-by-datasource")
    @Operation(summary = "按数据源获得数据源字段元数据列表（校验数据源归属）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:query')")
    public CommonResult<List<AiDatasourceColumnRespVO>> getAiDatasourceColumnListByDatasource(
            @RequestParam("datasourceId") Long datasourceId) {
        return success(BeanUtils.toBean(aiDatasourceColumnService.listByDatasourceId(datasourceId), AiDatasourceColumnRespVO.class));
    }

    @GetMapping("/list-by-table")
    @Operation(summary = "按表获得可见可查字段列表")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:query')")
    public CommonResult<List<AiDatasourceColumnRespVO>> getColumnListByTable(
            @RequestParam("tableId") Long tableId) {
        return success(BeanUtils.toBean(aiDatasourceColumnService.listVisibleColumns(tableId),
                AiDatasourceColumnRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出数据源字段元数据 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-column:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiDatasourceColumnExcel(@Valid AiDatasourceColumnPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiDatasourceColumn> list = aiDatasourceColumnService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "数据源字段元数据.xls", "数据", AiDatasourceColumnRespVO.class,
                BeanUtils.toBean(list, AiDatasourceColumnRespVO.class));
    }

    private LambdaQueryWrapper<AiDatasourceColumn> buildQuery(AiDatasourceColumnPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiDatasourceColumn>()
                .eqIfPresent(AiDatasourceColumn::getDatasourceId, reqVO.getDatasourceId())
                .eqIfPresent(AiDatasourceColumn::getTableId, reqVO.getTableId())
                .likeIfPresent(AiDatasourceColumn::getTableName, reqVO.getTableName())
                .likeIfPresent(AiDatasourceColumn::getColumnName, reqVO.getColumnName())
                .likeIfPresent(AiDatasourceColumn::getNameZh, reqVO.getNameZh())
                .eqIfPresent(AiDatasourceColumn::getSemanticRole, reqVO.getSemanticRole())
                .eqIfPresent(AiDatasourceColumn::getQueryable, reqVO.getQueryable())
                .eqIfPresent(AiDatasourceColumn::getSensitiveType, reqVO.getSensitiveType())
                .eqIfPresent(AiDatasourceColumn::getSyncStatus, reqVO.getSyncStatus())
                .betweenIfPresent(AiDatasourceColumn::getCreateTime, reqVO.getCreateTime())
                .eq(AiDatasourceColumn::getDeleted, 0)
                .orderByAsc(AiDatasourceColumn::getSort)
                .orderByDesc(AiDatasourceColumn::getId);
    }
}
