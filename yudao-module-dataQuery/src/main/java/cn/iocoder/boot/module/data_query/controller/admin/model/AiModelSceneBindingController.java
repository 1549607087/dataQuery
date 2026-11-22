package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.binding.AiModelSceneBindingPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.binding.AiModelSceneBindingRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.binding.AiModelSceneBindingSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.service.AiModelSceneBindingService;
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
 * 管理后台 - AI 模型场景绑定（路由与降级链）
 *
 * 说明：当前 Service 为 MyBatis Plus 通用 IService，故分页条件、逻辑删除在 Controller 内实现。
 */
@Tag(name = "管理后台 - AI 模型场景绑定")
@RestController
@RequestMapping("/data-query/ai-model-scene-binding")
@Validated
public class AiModelSceneBindingController {

    @Resource
    private AiModelSceneBindingService aiModelSceneBindingService;

    @PostMapping("/create")
    @Operation(summary = "创建模型场景绑定")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:create')")
    public CommonResult<Long> createAiModelSceneBinding(@Valid @RequestBody AiModelSceneBindingSaveReqVO createReqVO) {
        AiModelSceneBinding binding = BeanUtils.toBean(createReqVO, AiModelSceneBinding.class);
        fillCreateFields(binding);
        aiModelSceneBindingService.save(binding);
        return success(binding.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新模型场景绑定")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:update')")
    public CommonResult<Boolean> updateAiModelSceneBinding(@Valid @RequestBody AiModelSceneBindingSaveReqVO updateReqVO) {
        AiModelSceneBinding updateObj = BeanUtils.toBean(updateReqVO, AiModelSceneBinding.class);
        aiModelSceneBindingService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-enabled")
    @Operation(summary = "修改模型场景绑定启用状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:update')")
    public CommonResult<Boolean> updateAiModelSceneBindingEnabled(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("enabled") @Parameter(name = "enabled", description = "是否启用：0 否、1 是", required = true) Integer enabled) {
        AiModelSceneBinding updateObj = new AiModelSceneBinding();
        updateObj.setId(id);
        updateObj.setEnabled(enabled);
        aiModelSceneBindingService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模型场景绑定")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:delete')")
    public CommonResult<Boolean> deleteAiModelSceneBinding(@RequestParam("id") Long id) {
        aiModelSceneBindingService.update(new LambdaUpdateWrapper<AiModelSceneBinding>()
                .eq(AiModelSceneBinding::getId, id)
                .set(AiModelSceneBinding::getDeleted, 1));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除模型场景绑定")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:delete')")
    public CommonResult<Boolean> deleteAiModelSceneBindingList(@RequestParam("ids") List<Long> ids) {
        aiModelSceneBindingService.update(new LambdaUpdateWrapper<AiModelSceneBinding>()
                .in(AiModelSceneBinding::getId, ids)
                .set(AiModelSceneBinding::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得模型场景绑定")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:query')")
    public CommonResult<AiModelSceneBindingRespVO> getAiModelSceneBinding(@RequestParam("id") Long id) {
        AiModelSceneBinding binding = aiModelSceneBindingService.getAiModelSceneBinding(id);
        return success(BeanUtils.toBean(binding, AiModelSceneBindingRespVO.class));
    }

    @GetMapping("/get-by-scene")
    @Operation(summary = "按场景编码获得当前生效的模型绑定")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:query')")
    public CommonResult<AiModelSceneBindingRespVO> getAiModelSceneBindingByScene(
            @RequestParam("sceneCode") @Parameter(name = "sceneCode", description = "场景编码", required = true, example = "SQL_GEN") String sceneCode) {
        List<AiModelSceneBinding> list = aiModelSceneBindingService.list(new LambdaQueryWrapperX<AiModelSceneBinding>()
                .eq(AiModelSceneBinding::getSceneCode, sceneCode)
                .eq(AiModelSceneBinding::getEnabled, 1)
                .eq(AiModelSceneBinding::getDeleted, 0)
                .orderByAsc(AiModelSceneBinding::getSort));
        AiModelSceneBinding binding = list.isEmpty() ? null : list.get(0);
        return success(BeanUtils.toBean(binding, AiModelSceneBindingRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得模型场景绑定分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:query')")
    public CommonResult<PageResult<AiModelSceneBindingRespVO>> getAiModelSceneBindingPage(@Valid AiModelSceneBindingPageReqVO pageReqVO) {
        IPage<AiModelSceneBinding> page = aiModelSceneBindingService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelSceneBinding> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiModelSceneBindingRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出模型场景绑定 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-scene-binding:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiModelSceneBindingExcel(@Valid AiModelSceneBindingPageReqVO pageReqVO,
                                               HttpServletResponse response) throws IOException {
        List<AiModelSceneBinding> list = aiModelSceneBindingService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "模型场景绑定.xls", "数据", AiModelSceneBindingRespVO.class,
                BeanUtils.toBean(list, AiModelSceneBindingRespVO.class));
    }

    private LambdaQueryWrapper<AiModelSceneBinding> buildQuery(AiModelSceneBindingPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelSceneBinding>()
                .eqIfPresent(AiModelSceneBinding::getSceneCode, reqVO.getSceneCode())
                .likeIfPresent(AiModelSceneBinding::getSceneName, reqVO.getSceneName())
                .eqIfPresent(AiModelSceneBinding::getPrimaryModelId, reqVO.getPrimaryModelId())
                .eqIfPresent(AiModelSceneBinding::getEnabled, reqVO.getEnabled())
                .betweenIfPresent(AiModelSceneBinding::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelSceneBinding::getDeleted, 0)
                .orderByAsc(AiModelSceneBinding::getSort)
                .orderByDesc(AiModelSceneBinding::getId);
    }

    /**
     * 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段
     */
    private void fillCreateFields(AiModelSceneBinding entity) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String user = userId == null ? "" : userId.toString();
        entity.setId(null); // 由 MyBatis Plus 的 ASSIGN_ID 生成
        entity.setCreator(user);
        entity.setUpdater(user);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

}
