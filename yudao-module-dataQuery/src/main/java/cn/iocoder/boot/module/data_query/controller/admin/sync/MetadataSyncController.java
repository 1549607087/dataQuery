package cn.iocoder.boot.module.data_query.controller.admin.sync;

import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncStatusRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataVisibilityReqVO;
import cn.iocoder.boot.module.data_query.service.MetadataSyncService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 元数据同步与可见性维护（P1）
 *
 * 所有入口都以 datasourceId 为边界，Service 内经 AiDatasourceOwnerValidator 校验归属，
 * 查询由 TenantDatabaseInterceptor 追加 tenant_id；跨租户数据源一律按「不存在」处理。
 */
@Tag(name = "管理后台 - 元数据同步与可见性维护")
@RestController
@RequestMapping("/data-query/metadata-sync")
@Validated
public class MetadataSyncController {

    @Resource
    private MetadataSyncService metadataSyncService;

    @PostMapping("/sync")
    @Operation(summary = "同步数据源元数据（表 / 列 / 主键 / 外键）")
    @Parameter(name = "datasourceId", description = "数据源编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:metadata-sync:sync')")
    public CommonResult<MetadataSyncRespVO> sync(@RequestParam("datasourceId") Long datasourceId,
                                                 @RequestParam(value = "full", defaultValue = "false")
                                                 boolean full) {
        return success(metadataSyncService.sync(datasourceId, full));
    }

    @PutMapping("/visibility")
    @Operation(summary = "批量维护表 / 列的可见性与可查性（同时标记向量需重建）")
    @PreAuthorize("@ss.hasPermission('data-query:metadata-sync:update')")
    public CommonResult<Integer> updateVisibility(@Valid @RequestBody MetadataVisibilityReqVO reqVO) {
        return success(metadataSyncService.updateVisibility(reqVO));
    }

    @GetMapping("/status")
    @Operation(summary = "获得元数据同步状态与新鲜度概览")
    @Parameter(name = "datasourceId", description = "数据源编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:metadata-sync:query')")
    public CommonResult<MetadataSyncStatusRespVO> status(@RequestParam("datasourceId") Long datasourceId) {
        return success(metadataSyncService.status(datasourceId));
    }

}
