package cn.iocoder.boot.module.data_query.controller.admin.datasource;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation.AiDatasourceTableRelationPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation.AiDatasourceTableRelationRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.relation.AiDatasourceTableRelationSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceTableRelation;
import cn.iocoder.boot.module.data_query.service.AiDatasourceTableRelationService;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 管理后台 - 表关系 / Join 路径
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId，
 * 并按 datasource_id 校验数据源归属，防止把元数据写到其它租户的数据源下。
 */
@Tag(name = "管理后台 - 表关系 / Join 路径")
@RestController
@RequestMapping("/data-query/ai-datasource-table-relation")
@Validated
public class AiDatasourceTableRelationController {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private AiDatasourceTableRelationService aiDatasourceTableRelationService;

    @PostMapping("/create")
    @Operation(summary = "创建表关系 / Join 路径")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:create')")
    public CommonResult<Long> createAiDatasourceTableRelation(@Valid @RequestBody AiDatasourceTableRelationSaveReqVO reqVO) {
        return success(aiDatasourceTableRelationService.createAiDatasourceTableRelation(buildEntity(reqVO)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新表关系 / Join 路径")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:update')")
    public CommonResult<Boolean> updateAiDatasourceTableRelation(@Valid @RequestBody AiDatasourceTableRelationSaveReqVO reqVO) {
        aiDatasourceTableRelationService.updateAiDatasourceTableRelation(buildEntity(reqVO));
        return success(true);
    }

    /**
     * 前端传结构化的字段对列表，服务端拼成 join_pairs JSON 后入库。
     * 库里的 JSON 格式保持不变，召回侧解析逻辑无需改动。
     */
    private AiDatasourceTableRelation buildEntity(AiDatasourceTableRelationSaveReqVO reqVO) {
        final String joinPairsJson;
        try {
            joinPairsJson = JSON.writeValueAsString(
                    reqVO.getJoinPairs() == null ? List.of() : reqVO.getJoinPairs());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("连接字段对序列化失败", ex);
        }
        return BeanUtils.toBean(reqVO, AiDatasourceTableRelation.class,
                entity -> entity.setJoinPairs(joinPairsJson));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除表关系 / Join 路径")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:delete')")
    public CommonResult<Boolean> deleteAiDatasourceTableRelation(@RequestParam("id") Long id) {
        aiDatasourceTableRelationService.deleteAiDatasourceTableRelation(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除表关系 / Join 路径")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:delete')")
    public CommonResult<Boolean> deleteAiDatasourceTableRelationList(@RequestParam("ids") List<Long> ids) {
        aiDatasourceTableRelationService.deleteAiDatasourceTableRelationList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得表关系 / Join 路径")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:query')")
    public CommonResult<AiDatasourceTableRelationRespVO> getAiDatasourceTableRelation(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiDatasourceTableRelationService.getAiDatasourceTableRelation(id), AiDatasourceTableRelationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得表关系 / Join 路径分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:query')")
    public CommonResult<PageResult<AiDatasourceTableRelationRespVO>> getAiDatasourceTableRelationPage(@Valid AiDatasourceTableRelationPageReqVO pageReqVO) {
        IPage<AiDatasourceTableRelation> page = aiDatasourceTableRelationService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiDatasourceTableRelation> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiDatasourceTableRelationRespVO.class));
    }

    @GetMapping("/list-by-datasource")
    @Operation(summary = "按数据源获得表关系 / Join 路径列表（校验数据源归属）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:query')")
    public CommonResult<List<AiDatasourceTableRelationRespVO>> getAiDatasourceTableRelationListByDatasource(
            @RequestParam("datasourceId") Long datasourceId) {
        return success(BeanUtils.toBean(aiDatasourceTableRelationService.listByDatasourceId(datasourceId), AiDatasourceTableRelationRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出表关系 / Join 路径 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource-table-relation:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiDatasourceTableRelationExcel(@Valid AiDatasourceTableRelationPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiDatasourceTableRelation> list = aiDatasourceTableRelationService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "表关系 / Join 路径.xls", "数据", AiDatasourceTableRelationRespVO.class,
                BeanUtils.toBean(list, AiDatasourceTableRelationRespVO.class));
    }

    private LambdaQueryWrapper<AiDatasourceTableRelation> buildQuery(AiDatasourceTableRelationPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiDatasourceTableRelation>()
                .eqIfPresent(AiDatasourceTableRelation::getDatasourceId, reqVO.getDatasourceId())
                .eqIfPresent(AiDatasourceTableRelation::getLeftTableId, reqVO.getLeftTableId())
                .eqIfPresent(AiDatasourceTableRelation::getRightTableId, reqVO.getRightTableId())
                .eqIfPresent(AiDatasourceTableRelation::getVerified, reqVO.getVerified())
                .eqIfPresent(AiDatasourceTableRelation::getEnabled, reqVO.getEnabled())
                .eqIfPresent(AiDatasourceTableRelation::getStatus, reqVO.getStatus())
                .betweenIfPresent(AiDatasourceTableRelation::getCreateTime, reqVO.getCreateTime())
                .eq(AiDatasourceTableRelation::getDeleted, 0)
                .orderByAsc(AiDatasourceTableRelation::getSort)
                .orderByDesc(AiDatasourceTableRelation::getId);
    }
}
