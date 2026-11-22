package cn.iocoder.boot.module.data_query.controller.admin.prompt;

import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene.AiPromptScenePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene.AiPromptSceneRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene.AiPromptSceneSaveReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.scene.AiPromptSceneSimpleRespVO;
import cn.iocoder.boot.module.data_query.entity.AiPromptScene;
import cn.iocoder.boot.module.data_query.service.AiPromptSceneService;
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
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 提示词场景字典（12 个场景：意图识别 / 问题改写 / Schema 召回 / SQL 生成 ...）
 *
 * 场景编码与 ai_model_scene_binding.scene_code 共用，新增场景时须同步维护模型侧绑定。
 */
@Tag(name = "管理后台 - 提示词场景字典")
@RestController
@RequestMapping("/data-query/ai-prompt-scene")
@Validated
public class AiPromptSceneController {

    @Resource
    private AiPromptSceneService aiPromptSceneService;

    @PostMapping("/create")
    @Operation(summary = "创建提示词场景")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:create')")
    public CommonResult<Long> createAiPromptScene(@Valid @RequestBody AiPromptSceneSaveReqVO createReqVO) {
        AiPromptScene scene = BeanUtils.toBean(createReqVO, AiPromptScene.class);
        fillCreateFields(scene);
        aiPromptSceneService.save(scene);
        return success(scene.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新提示词场景")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:update')")
    public CommonResult<Boolean> updateAiPromptScene(@Valid @RequestBody AiPromptSceneSaveReqVO updateReqVO) {
        AiPromptScene updateObj = BeanUtils.toBean(updateReqVO, AiPromptScene.class);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiPromptSceneService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改提示词场景状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:update')")
    public CommonResult<Boolean> updateAiPromptSceneStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 启用、1 停用", required = true) Integer status) {
        AiPromptScene updateObj = new AiPromptScene();
        updateObj.setId(id);
        updateObj.setStatus(status);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiPromptSceneService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除提示词场景")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:delete')")
    public CommonResult<Boolean> deleteAiPromptScene(@RequestParam("id") Long id) {
        aiPromptSceneService.update(new LambdaUpdateWrapper<AiPromptScene>()
                .eq(AiPromptScene::getId, id)
                .set(AiPromptScene::getDeleted, 1));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除提示词场景")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:delete')")
    public CommonResult<Boolean> deleteAiPromptSceneList(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return success(true);
        }
        aiPromptSceneService.update(new LambdaUpdateWrapper<AiPromptScene>()
                .in(AiPromptScene::getId, ids)
                .set(AiPromptScene::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得提示词场景")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:query')")
    public CommonResult<AiPromptSceneRespVO> getAiPromptScene(@RequestParam("id") Long id) {
        AiPromptScene scene = aiPromptSceneService.getAiPromptScene(id);
        return success(BeanUtils.toBean(scene, AiPromptSceneRespVO.class));
    }

    @GetMapping("/get-by-code")
    @Operation(summary = "按场景编码获得场景定义")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:query')")
    public CommonResult<AiPromptSceneRespVO> getAiPromptSceneByCode(
            @RequestParam("sceneCode") @Parameter(name = "sceneCode", description = "场景编码", required = true, example = "SQL_GEN") String sceneCode) {
        AiPromptScene scene = aiPromptSceneService.getAiPromptSceneByCode(sceneCode);
        return success(BeanUtils.toBean(scene, AiPromptSceneRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得提示词场景分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:query')")
    public CommonResult<PageResult<AiPromptSceneRespVO>> getAiPromptScenePage(@Valid AiPromptScenePageReqVO pageReqVO) {
        IPage<AiPromptScene> page = aiPromptSceneService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiPromptScene> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiPromptSceneRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用的提示词场景精简列表")
    public CommonResult<List<AiPromptSceneSimpleRespVO>> getAiPromptSceneSimpleList() {
        List<AiPromptScene> list = aiPromptSceneService.list(new LambdaQueryWrapperX<AiPromptScene>()
                .eq(AiPromptScene::getStatus, 0)
                .eq(AiPromptScene::getDeleted, 0)
                .orderByAsc(AiPromptScene::getSort));
        return success(BeanUtils.toBean(list, AiPromptSceneSimpleRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出提示词场景 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-scene:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiPromptSceneExcel(@Valid AiPromptScenePageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        List<AiPromptScene> list = aiPromptSceneService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "提示词场景.xls", "数据", AiPromptSceneRespVO.class,
                BeanUtils.toBean(list, AiPromptSceneRespVO.class));
    }

    private LambdaQueryWrapper<AiPromptScene> buildQuery(AiPromptScenePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiPromptScene>()
                .likeIfPresent(AiPromptScene::getSceneCode, reqVO.getSceneCode())
                .likeIfPresent(AiPromptScene::getSceneName, reqVO.getSceneName())
                .eqIfPresent(AiPromptScene::getStatus, reqVO.getStatus())
                .eqIfPresent(AiPromptScene::getDefaultModelType, reqVO.getDefaultModelType())
                .betweenIfPresent(AiPromptScene::getCreateTime, reqVO.getCreateTime())
                .eq(AiPromptScene::getDeleted, 0)
                .orderByAsc(AiPromptScene::getSort)
                .orderByDesc(AiPromptScene::getId);
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

    /** 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段 */
    private void fillCreateFields(AiPromptScene entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 MyBatis Plus 的 ASSIGN_ID 生成
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

}
