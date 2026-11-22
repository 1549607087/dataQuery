package cn.iocoder.boot.module.data_query.controller.admin.datasource;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourcePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceSaveReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceSimpleRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceTestRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.service.AiDatasourceService;
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
import org.springframework.util.StringUtils;
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
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 数据源（连接配置、方言、只读隔离、SQL 规则、健康状态）
 *
 * 安全口径：
 * 1. 密码 / 只读密码加密存储（AiSecretTypeHandler），任何响应都不回显，只给「是否已配置」状态位；
 * 2. 更新时密码留空表示不修改，避免把已有密文覆盖成空值；
 * 3. 数据源侧 6 张表全部租户私有，写入在 Service 内显式 setTenantId，
 *    元数据子表的写入还要按 datasource_id 校验归属（AiDatasourceOwnerValidator）。
 */
@Tag(name = "管理后台 - 数据源")
@RestController
@RequestMapping("/data-query/ai-datasource")
@Validated
public class AiDatasourceController {

    @Resource
    private AiDatasourceService aiDatasourceService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:create')")
    public CommonResult<Long> createAiDatasource(@Valid @RequestBody AiDatasourceSaveReqVO reqVO) {
        return success(aiDatasourceService.createDatasource(BeanUtils.toBean(reqVO, AiDatasource.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:update')")
    public CommonResult<Boolean> updateAiDatasource(@Valid @RequestBody AiDatasourceSaveReqVO reqVO) {
        aiDatasourceService.updateDatasource(BeanUtils.toBean(reqVO, AiDatasource.class));
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改数据源状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:update')")
    public CommonResult<Boolean> updateAiDatasourceStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 启用、1 停用、2 维护中", required = true) Integer status) {
        AiDatasource entity = new AiDatasource();
        entity.setId(id);
        entity.setStatus(status);
        aiDatasourceService.updateDatasource(entity);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:delete')")
    public CommonResult<Boolean> deleteAiDatasource(@RequestParam("id") Long id) {
        aiDatasourceService.deleteDatasource(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除数据源")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:delete')")
    public CommonResult<Boolean> deleteAiDatasourceList(@RequestParam("ids") List<Long> ids) {
        aiDatasourceService.deleteDatasourceList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:query')")
    public CommonResult<AiDatasourceRespVO> getAiDatasource(@RequestParam("id") Long id) {
        return success(toRespVO(aiDatasourceService.getDatasource(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:query')")
    public CommonResult<PageResult<AiDatasourceRespVO>> getAiDatasourcePage(@Valid AiDatasourcePageReqVO pageReqVO) {
        IPage<AiDatasource> page = aiDatasourceService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiDatasource> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        PageResult<AiDatasourceRespVO> voPage = BeanUtils.toBean(pageResult, AiDatasourceRespVO.class);
        if (voPage.getList() != null) {
            fillConfigured(voPage.getList(), page.getRecords());
        }
        return success(voPage);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用的数据源精简列表")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:query')")
    public CommonResult<List<AiDatasourceSimpleRespVO>> getAiDatasourceSimpleList() {
        return success(BeanUtils.toBean(aiDatasourceService.listEnabledDatasources(),
                AiDatasourceSimpleRespVO.class));
    }

    @PostMapping("/test-connection")
    @Operation(summary = "连通性检测（管理连接；回写健康状态并记同步日志）")
    @Parameter(name = "id", description = "数据源编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:update')")
    public CommonResult<AiDatasourceTestRespVO> testAiDatasourceConnection(@RequestParam("id") Long id) {
        return success(aiDatasourceService.testConnection(id));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出数据源 Excel（不含凭证）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-datasource:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiDatasourceExcel(@Valid AiDatasourcePageReqVO pageReqVO,
                                        HttpServletResponse response) throws IOException {
        List<AiDatasource> list = aiDatasourceService.list(buildQuery(pageReqVO));
        List<AiDatasourceRespVO> vos = BeanUtils.toBean(list, AiDatasourceRespVO.class);
        fillConfigured(vos, list);
        ExcelUtils.write(response, "数据源.xls", "数据", AiDatasourceRespVO.class, vos);
    }

    /** 响应只带「是否已配置」状态位，不含任何凭证内容 */
    private AiDatasourceRespVO toRespVO(AiDatasource datasource) {
        if (datasource == null) {
            return null;
        }
        AiDatasourceRespVO vo = BeanUtils.toBean(datasource, AiDatasourceRespVO.class);
        vo.setPasswordConfigured(StringUtils.hasText(datasource.getPassword()));
        vo.setReadonlyPasswordConfigured(StringUtils.hasText(datasource.getReadonlyPassword()));
        return vo;
    }

    private void fillConfigured(List<AiDatasourceRespVO> vos, List<AiDatasource> entities) {
        if (vos == null || entities == null) {
            return;
        }
        Map<Long, AiDatasource> byId = entities.stream().collect(Collectors.toMap(
                AiDatasource::getId, d -> d, (a, b) -> a));
        for (AiDatasourceRespVO vo : vos) {
            AiDatasource entity = byId.get(vo.getId());
            if (entity == null) {
                vo.setPasswordConfigured(Boolean.FALSE);
                vo.setReadonlyPasswordConfigured(Boolean.FALSE);
                continue;
            }
            vo.setPasswordConfigured(StringUtils.hasText(entity.getPassword()));
            vo.setReadonlyPasswordConfigured(StringUtils.hasText(entity.getReadonlyPassword()));
        }
    }

    private LambdaQueryWrapper<AiDatasource> buildQuery(AiDatasourcePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiDatasource>()
                .likeIfPresent(AiDatasource::getName, reqVO.getName())
                .eqIfPresent(AiDatasource::getCode, reqVO.getCode())
                .eqIfPresent(AiDatasource::getDsType, reqVO.getDsType())
                .eqIfPresent(AiDatasource::getDialect, reqVO.getDialect())
                .eqIfPresent(AiDatasource::getStatus, reqVO.getStatus())
                .eqIfPresent(AiDatasource::getHealthStatus, reqVO.getHealthStatus())
                .eqIfPresent(AiDatasource::getSyncStatus, reqVO.getSyncStatus())
                .betweenIfPresent(AiDatasource::getCreateTime, reqVO.getCreateTime())
                .eq(AiDatasource::getDeleted, 0)
                .orderByAsc(AiDatasource::getSort)
                .orderByDesc(AiDatasource::getId);
    }

}
