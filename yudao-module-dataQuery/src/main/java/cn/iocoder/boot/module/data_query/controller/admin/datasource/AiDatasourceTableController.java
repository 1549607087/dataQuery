package cn.iocoder.boot.module.data_query.controller.admin.datasource;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.table.AiDatasourceTablePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.table.AiDatasourceTableRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.table.AiDatasourceTableSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTable;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableService;
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
 * 管理后台 - 数据源表元数据
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId，
 * 并按 datasource_id 校验数据源归属，防止把元数据写到其它租户的数据源下。
 */
@Tag(name = "管理后台 - 数据源表元数据")
@RestController
@RequestMapping("/data-query/ai-datasource-table")
@Validated
public class AiDatasourceTableController {

    @Resource
    private AiDatasourceTableService aiDatasourceTableService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源表元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:create')")
    public CommonResult<Long> createAiDatasourceTable(@Valid @RequestBody AiDatasourceTableSaveReqVO reqVO) {
        return success(aiDatasourceTableService.createAiDatasourceTable(BeanUtils.toBean(reqVO, AiDatasourceTable.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源表元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:update')")
    public CommonResult<Boolean> updateAiDatasourceTable(@Valid @RequestBody AiDatasourceTableSaveReqVO reqVO) {
        aiDatasourceTableService.updateAiDatasourceTable(BeanUtils.toBean(reqVO, AiDatasourceTable.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源表元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:delete')")
    public CommonResult<Boolean> deleteAiDatasourceTable(@RequestParam("id") Long id) {
        aiDatasourceTableService.deleteAiDatasourceTable(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除数据源表元数据")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:delete')")
    public CommonResult<Boolean> deleteAiDatasourceTableList(@RequestParam("ids") List<Long> ids) {
        aiDatasourceTableService.deleteAiDatasourceTableList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源表元数据")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:query')")
    public CommonResult<AiDatasourceTableRespVO> getAiDatasourceTable(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiDatasourceTableService.getAiDatasourceTable(id), AiDatasourceTableRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源表元数据分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:query')")
    public CommonResult<PageResult<AiDatasourceTableRespVO>> getAiDatasourceTablePage(@Valid AiDatasourceTablePageReqVO pageReqVO) {
        IPage<AiDatasourceTable> page = aiDatasourceTableService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiDatasourceTable> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiDatasourceTableRespVO.class));
    }

    @GetMapping("/list-by-datasource")
    @Operation(summary = "按数据源获得数据源表元数据列表（校验数据源归属）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:query')")
    public CommonResult<List<AiDatasourceTableRespVO>> getAiDatasourceTableListByDatasource(
            @RequestParam("datasourceId") Long datasourceId) {
        return success(BeanUtils.toBean(aiDatasourceTableService.listByDatasourceId(datasourceId), AiDatasourceTableRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出数据源表元数据 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiDatasourceTableExcel(@Valid AiDatasourceTablePageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiDatasourceTable> list = aiDatasourceTableService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "数据源表元数据.xls", "数据", AiDatasourceTableRespVO.class,
                BeanUtils.toBean(list, AiDatasourceTableRespVO.class));
    }

    private LambdaQueryWrapper<AiDatasourceTable> buildQuery(AiDatasourceTablePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiDatasourceTable>()
                .eqIfPresent(AiDatasourceTable::getDatasourceId, reqVO.getDatasourceId())
                .likeIfPresent(AiDatasourceTable::getTableName, reqVO.getTableName())
                .likeIfPresent(AiDatasourceTable::getNameZh, reqVO.getNameZh())
                .eqIfPresent(AiDatasourceTable::getTableType, reqVO.getTableType())
                .eqIfPresent(AiDatasourceTable::getVisible, reqVO.getVisible())
                .eqIfPresent(AiDatasourceTable::getQueryable, reqVO.getQueryable())
                .eqIfPresent(AiDatasourceTable::getSyncStatus, reqVO.getSyncStatus())
                .betweenIfPresent(AiDatasourceTable::getCreateTime, reqVO.getCreateTime())
                .eq(AiDatasourceTable::getDeleted, 0)
                .orderByAsc(AiDatasourceTable::getSort)
                .orderByDesc(AiDatasourceTable::getId);
    }
}
