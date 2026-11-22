package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.experiment.AiModelGrayExperimentPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.experiment.AiModelGrayExperimentRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.experiment.AiModelGrayExperimentSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiModelGrayExperiment;
import cn.iocoder.boot.module.data_query.service.AiModelGrayExperimentService;
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
 * 管理后台 - 灰度实验
 *
 * 约束：gray_object 一次只允许 MODEL 或 PROMPT 一个变量，避免模型与提示词双灰度污染归因；
 * 未达到 min_sample_size 不允许下结论；分桶必须稳定，否则同一用户两次提问命中不同组。
 */
@Tag(name = "管理后台 - 模型灰度实验")
@RestController
@RequestMapping("/data-query/ai-model-gray-experiment")
@Validated
public class AiModelGrayExperimentController {

    @Resource
    private AiModelGrayExperimentService aiModelGrayExperimentService;

    @PostMapping("/create")
    @Operation(summary = "创建灰度实验")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:create')")
    public CommonResult<Long> create(@Valid @RequestBody AiModelGrayExperimentSaveReqVO createReqVO) {
        AiModelGrayExperiment entity = BeanUtils.toBean(createReqVO, AiModelGrayExperiment.class);
        fillCreateFields(entity);
        aiModelGrayExperimentService.save(entity);
        return success(entity.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新灰度实验")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody AiModelGrayExperimentSaveReqVO updateReqVO) {
        AiModelGrayExperiment updateObj = BeanUtils.toBean(updateReqVO, AiModelGrayExperiment.class);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiModelGrayExperimentService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改灰度实验状态（开始 / 结束 / 全量 / 回滚）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:update')")
    public CommonResult<Boolean> updateStatus(@RequestParam("id") Long id,
                                              @RequestParam("status") Integer status) {
        AiModelGrayExperiment updateObj = new AiModelGrayExperiment();
        updateObj.setId(id);
        updateObj.setStatus(status);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiModelGrayExperimentService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除灰度实验")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        aiModelGrayExperimentService.update(new LambdaUpdateWrapper<AiModelGrayExperiment>()
                .eq(AiModelGrayExperiment::getId, id)
                .set(AiModelGrayExperiment::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得灰度实验")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:query')")
    public CommonResult<AiModelGrayExperimentRespVO> get(@RequestParam("id") Long id) {
        AiModelGrayExperiment entity = aiModelGrayExperimentService.getAiModelGrayExperiment(id);
        return success(BeanUtils.toBean(entity, AiModelGrayExperimentRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得灰度实验分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:query')")
    public CommonResult<PageResult<AiModelGrayExperimentRespVO>> page(@Valid AiModelGrayExperimentPageReqVO pageReqVO) {
        IPage<AiModelGrayExperiment> page = aiModelGrayExperimentService.page(
                MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelGrayExperiment> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiModelGrayExperimentRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出灰度实验 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-gray-experiment:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportExcel(@Valid AiModelGrayExperimentPageReqVO pageReqVO,
                            HttpServletResponse response) throws IOException {
        List<AiModelGrayExperiment> list = aiModelGrayExperimentService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "灰度实验.xls", "数据", AiModelGrayExperimentRespVO.class,
                BeanUtils.toBean(list, AiModelGrayExperimentRespVO.class));
    }

    private LambdaQueryWrapper<AiModelGrayExperiment> buildQuery(AiModelGrayExperimentPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelGrayExperiment>()
                .likeIfPresent(AiModelGrayExperiment::getName, reqVO.getName())
                .eqIfPresent(AiModelGrayExperiment::getGrayObject, reqVO.getGrayObject())
                .eqIfPresent(AiModelGrayExperiment::getSceneCode, reqVO.getSceneCode())
                .eqIfPresent(AiModelGrayExperiment::getStatus, reqVO.getStatus())
                .betweenIfPresent(AiModelGrayExperiment::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelGrayExperiment::getDeleted, 0)
                .orderByDesc(AiModelGrayExperiment::getId);
    }

    private void fillCreateFields(AiModelGrayExperiment entity) {
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
