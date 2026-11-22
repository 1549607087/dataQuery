package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.config.AiModelConfigPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.config.AiModelConfigRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.config.AiModelConfigSaveReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.config.AiModelConfigSimpleRespVO;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.service.AiModelConfigService;
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
 * 管理后台 - AI 模型配置（参数、价格、限流、灰度、健康状态）
 *
 * 说明：当前 Service 为 MyBatis Plus 通用 IService，故分页条件、逻辑删除在 Controller 内实现；
 * 连通性检测 / 试调用需要 LLM 客户端支撑，待 Service 层补齐后再开放对应接口。
 */
@Tag(name = "管理后台 - AI 模型配置")
@RestController
@RequestMapping("/data-query/ai-model-config")
@Validated
public class AiModelConfigController {

    @Resource
    private AiModelConfigService aiModelConfigService;

    @PostMapping("/create")
    @Operation(summary = "创建模型配置")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:create')")
    public CommonResult<Long> createAiModelConfig(@Valid @RequestBody AiModelConfigSaveReqVO createReqVO) {
        AiModelConfig config = BeanUtils.toBean(createReqVO, AiModelConfig.class);
        fillCreateFields(config);
        aiModelConfigService.save(config);
        return success(config.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新模型配置")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:update')")
    public CommonResult<Boolean> updateAiModelConfig(@Valid @RequestBody AiModelConfigSaveReqVO updateReqVO) {
        AiModelConfig updateObj = BeanUtils.toBean(updateReqVO, AiModelConfig.class);
        aiModelConfigService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改模型配置状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:update')")
    public CommonResult<Boolean> updateAiModelConfigStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 启用、1 停用、2 灰度", required = true) Integer status) {
        AiModelConfig updateObj = new AiModelConfig();
        updateObj.setId(id);
        updateObj.setStatus(status);
        aiModelConfigService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模型配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:delete')")
    public CommonResult<Boolean> deleteAiModelConfig(@RequestParam("id") Long id) {
        aiModelConfigService.update(new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getDeleted, 1));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除模型配置")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:delete')")
    public CommonResult<Boolean> deleteAiModelConfigList(@RequestParam("ids") List<Long> ids) {
        aiModelConfigService.update(new LambdaUpdateWrapper<AiModelConfig>()
                .in(AiModelConfig::getId, ids)
                .set(AiModelConfig::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得模型配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:query')")
    public CommonResult<AiModelConfigRespVO> getAiModelConfig(@RequestParam("id") Long id) {
        AiModelConfig config = aiModelConfigService.getAiModelConfig(id);
        return success(BeanUtils.toBean(config, AiModelConfigRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得模型配置分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:query')")
    public CommonResult<PageResult<AiModelConfigRespVO>> getAiModelConfigPage(@Valid AiModelConfigPageReqVO pageReqVO) {
        IPage<AiModelConfig> page = aiModelConfigService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelConfig> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiModelConfigRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用的模型配置精简列表")
    public CommonResult<List<AiModelConfigSimpleRespVO>> getAiModelConfigSimpleList(
            @RequestParam(value = "modelType", required = false)
            @Parameter(name = "modelType", description = "按模型类型过滤，不传则返回全部") Integer modelType) {
        List<AiModelConfig> list = aiModelConfigService.list(new LambdaQueryWrapperX<AiModelConfig>()
                .eqIfPresent(AiModelConfig::getModelType, modelType)
                .eq(AiModelConfig::getStatus, 0)
                .eq(AiModelConfig::getDeleted, 0)
                .orderByAsc(AiModelConfig::getSort));
        return success(BeanUtils.toBean(list, AiModelConfigSimpleRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出模型配置 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-config:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiModelConfigExcel(@Valid AiModelConfigPageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        List<AiModelConfig> list = aiModelConfigService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "模型配置.xls", "数据", AiModelConfigRespVO.class,
                BeanUtils.toBean(list, AiModelConfigRespVO.class));
    }

    private LambdaQueryWrapper<AiModelConfig> buildQuery(AiModelConfigPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelConfig>()
                .likeIfPresent(AiModelConfig::getName, reqVO.getName())
                .likeIfPresent(AiModelConfig::getModelId, reqVO.getModelId())
                .eqIfPresent(AiModelConfig::getProviderId, reqVO.getProviderId())
                .eqIfPresent(AiModelConfig::getModelType, reqVO.getModelType())
                .eqIfPresent(AiModelConfig::getStatus, reqVO.getStatus())
                .eqIfPresent(AiModelConfig::getHealthStatus, reqVO.getHealthStatus())
                .betweenIfPresent(AiModelConfig::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelConfig::getDeleted, 0)
                .orderByAsc(AiModelConfig::getSort)
                .orderByDesc(AiModelConfig::getId);
    }

    /**
     * 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段
     */
    private void fillCreateFields(AiModelConfig entity) {
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
