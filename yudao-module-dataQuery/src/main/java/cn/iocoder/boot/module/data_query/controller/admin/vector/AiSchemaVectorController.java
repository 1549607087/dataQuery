package cn.iocoder.boot.module.data_query.controller.admin.vector;

import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.AiSchemaVectorPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.AiSchemaVectorRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorBuildRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorStatusRespVO;
import cn.iocoder.boot.module.data_query.service.AiSchemaVectorService;
import cn.iocoder.boot.module.data_query.service.SchemaVectorBuildService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - Schema 向量索引（P1 写入侧）
 *
 * 所有入口以 datasourceId 为边界，经 AiDatasourceOwnerValidator 校验归属；
 * 响应永远不返回 embedding 字面量与 tenant_id。
 * P1 阶段 Embedding 未接线，sync 会构建 Document 但向量为 NULL，status 如实暴露待构建数。
 */
@Tag(name = "管理后台 - Schema 向量索引")
@RestController
@RequestMapping("/data-query/schema-vector")
@Validated
public class AiSchemaVectorController {

    @Resource
    private SchemaVectorBuildService schemaVectorBuildService;
    @Resource
    private AiSchemaVectorService aiSchemaVectorService;

    @PostMapping("/sync")
    @Operation(summary = "增量构建 Schema 向量索引（按 content_hash 跳过未变更内容）")
    @Parameter(name = "datasourceId", description = "数据源编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:schema-vector:sync')")
    public CommonResult<SchemaVectorBuildRespVO> sync(@RequestParam("datasourceId") Long datasourceId) {
        return success(schemaVectorBuildService.build(datasourceId, false));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "全量重建当前数据源的 Schema 向量索引")
    @Parameter(name = "datasourceId", description = "数据源编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:schema-vector:rebuild')")
    public CommonResult<SchemaVectorBuildRespVO> rebuild(@RequestParam("datasourceId") Long datasourceId) {
        return success(schemaVectorBuildService.build(datasourceId, true));
    }

    @GetMapping("/status")
    @Operation(summary = "获得 Schema 向量索引新鲜度与 Embedding 接线状态")
    @Parameter(name = "datasourceId", description = "数据源编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:schema-vector:query')")
    public CommonResult<SchemaVectorStatusRespVO> status(@RequestParam("datasourceId") Long datasourceId) {
        return success(schemaVectorBuildService.status(datasourceId));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 Schema 向量分页（不返回向量字面量）")
    @PreAuthorize("@ss.hasPermission('data-query:schema-vector:query')")
    public CommonResult<PageResult<AiSchemaVectorRespVO>> page(@Valid AiSchemaVectorPageReqVO reqVO) {
        return success(schemaVectorBuildService.page(reqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "下线一条 Schema 向量（逻辑删除，不物理删）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:schema-vector:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        aiSchemaVectorService.deleteVector(id);
        return success(true);
    }

}
