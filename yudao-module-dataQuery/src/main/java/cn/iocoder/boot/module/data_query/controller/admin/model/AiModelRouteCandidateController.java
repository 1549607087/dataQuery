package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.candidate.AiModelRouteCandidatePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.candidate.AiModelRouteCandidateRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.candidate.AiModelRouteCandidateSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiModelRouteCandidate;
import cn.iocoder.boot.module.data_query.service.AiModelRouteCandidateService;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 模型路由候选集
 *
 * 用途：为 route_strategy = 2 优先级 / 3 权重 / 4 成本优先 提供候选模型列表。
 * 运行时（ModelRouter）优先读本表；查不到时回落解析 ai_model_scene_binding.fallback_model_ids。
 */
@Tag(name = "管理后台 - 模型路由候选集")
@RestController
@RequestMapping("/data-query/ai-model-route-candidate")
@Validated
public class AiModelRouteCandidateController {

    @Resource
    private AiModelRouteCandidateService aiModelRouteCandidateService;

    @PostMapping("/create")
    @Operation(summary = "创建模型路由候选")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:create')")
    public CommonResult<Long> create(@Valid @RequestBody AiModelRouteCandidateSaveReqVO createReqVO) {
        AiModelRouteCandidate entity = BeanUtils.toBean(createReqVO, AiModelRouteCandidate.class);
        fillCreateFields(entity);
        aiModelRouteCandidateService.save(entity);
        return success(entity.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新模型路由候选")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody AiModelRouteCandidateSaveReqVO updateReqVO) {
        AiModelRouteCandidate updateObj = BeanUtils.toBean(updateReqVO, AiModelRouteCandidate.class);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiModelRouteCandidateService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模型路由候选")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        aiModelRouteCandidateService.update(new LambdaUpdateWrapper<AiModelRouteCandidate>()
                .eq(AiModelRouteCandidate::getId, id)
                .set(AiModelRouteCandidate::getDeleted, 1));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除模型路由候选")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:delete')")
    public CommonResult<Boolean> deleteList(@RequestParam("ids") List<Long> ids) {
        aiModelRouteCandidateService.update(new LambdaUpdateWrapper<AiModelRouteCandidate>()
                .in(AiModelRouteCandidate::getId, ids)
                .set(AiModelRouteCandidate::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得模型路由候选")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:query')")
    public CommonResult<AiModelRouteCandidateRespVO> get(@RequestParam("id") Long id) {
        AiModelRouteCandidate entity = aiModelRouteCandidateService.getAiModelRouteCandidate(id);
        return success(BeanUtils.toBean(entity, AiModelRouteCandidateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得模型路由候选分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:query')")
    public CommonResult<PageResult<AiModelRouteCandidateRespVO>> page(@Valid AiModelRouteCandidatePageReqVO pageReqVO) {
        IPage<AiModelRouteCandidate> page = aiModelRouteCandidateService.page(
                MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelRouteCandidate> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiModelRouteCandidateRespVO.class));
    }

    @GetMapping("/list-by-binding")
    @Operation(summary = "按场景绑定获得参与路由的候选列表")
    @Parameter(name = "bindingId", description = "场景绑定编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:query')")
    public CommonResult<List<AiModelRouteCandidateRespVO>> listByBinding(@RequestParam("bindingId") Long bindingId) {
        List<AiModelRouteCandidate> list = aiModelRouteCandidateService.list(
                new LambdaQueryWrapperX<AiModelRouteCandidate>()
                        .eq(AiModelRouteCandidate::getBindingId, bindingId)
                        .eq(AiModelRouteCandidate::getEnabled, 1)
                        .eq(AiModelRouteCandidate::getDeleted, 0)
                        .orderByAsc(AiModelRouteCandidate::getPriority)
                        .orderByAsc(AiModelRouteCandidate::getSort));
        return success(BeanUtils.toBean(list, AiModelRouteCandidateRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出模型路由候选 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-route-candidate:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportExcel(@Valid AiModelRouteCandidatePageReqVO pageReqVO,
                            HttpServletResponse response) throws IOException {
        List<AiModelRouteCandidate> list = aiModelRouteCandidateService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "模型路由候选.xls", "数据", AiModelRouteCandidateRespVO.class,
                BeanUtils.toBean(list, AiModelRouteCandidateRespVO.class));
    }

    private LambdaQueryWrapper<AiModelRouteCandidate> buildQuery(AiModelRouteCandidatePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelRouteCandidate>()
                .eqIfPresent(AiModelRouteCandidate::getBindingId, reqVO.getBindingId())
                .eqIfPresent(AiModelRouteCandidate::getModelId, reqVO.getModelId())
                .eqIfPresent(AiModelRouteCandidate::getProviderId, reqVO.getProviderId())
                .eqIfPresent(AiModelRouteCandidate::getEnabled, reqVO.getEnabled())
                .betweenIfPresent(AiModelRouteCandidate::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelRouteCandidate::getDeleted, 0)
                .orderByAsc(AiModelRouteCandidate::getPriority)
                .orderByDesc(AiModelRouteCandidate::getId);
    }

    private void fillCreateFields(AiModelRouteCandidate entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null);
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

}
