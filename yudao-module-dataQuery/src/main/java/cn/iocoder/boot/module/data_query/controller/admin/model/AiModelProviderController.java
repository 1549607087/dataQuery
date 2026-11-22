package cn.iocoder.boot.module.data_query.controller.admin.model;

import cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider.AiModelProviderPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider.AiModelProviderRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider.AiModelProviderSaveReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.model.vo.provider.AiModelProviderSimpleRespVO;
import cn.iocoder.boot.module.data_query.entity.AiModelProvider;
import cn.iocoder.boot.module.data_query.service.AiModelProviderService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - AI 模型供应商（接入凭证）
 *
 * 说明：当前 Service 为 MyBatis Plus 通用 IService，故分页条件、逻辑删除在 Controller 内实现；
 * 后续若按芋道规范补齐 Service 方法，可将 buildQuery / 删除逻辑下沉，接口形态保持不变。
 */
@Tag(name = "管理后台 - AI 模型供应商")
@RestController
@RequestMapping("/data-query/ai-model-provider")
@Validated
public class AiModelProviderController {

    @Resource
    private AiModelProviderService aiModelProviderService;

    @PostMapping("/create")
    @Operation(summary = "创建模型供应商")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:create')")
    public CommonResult<Long> createAiModelProvider(@Valid @RequestBody AiModelProviderSaveReqVO createReqVO) {
        AiModelProvider provider = BeanUtils.toBean(createReqVO, AiModelProvider.class);
        fillCreateFields(provider);
        aiModelProviderService.save(provider);
        return success(provider.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新模型供应商")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:update')")
    public CommonResult<Boolean> updateAiModelProvider(@Valid @RequestBody AiModelProviderSaveReqVO updateReqVO) {
        AiModelProvider updateObj = BeanUtils.toBean(updateReqVO, AiModelProvider.class);
        // 密钥留空表示不修改，避免把已配置的密文覆盖成空值
        if (!StringUtils.hasText(updateObj.getApiKey())) {
            updateObj.setApiKey(null);
        }
        if (!StringUtils.hasText(updateObj.getApiSecret())) {
            updateObj.setApiSecret(null);
        }
        aiModelProviderService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改模型供应商状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:update')")
    public CommonResult<Boolean> updateAiModelProviderStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 启用、1 停用", required = true) Integer status) {
        AiModelProvider updateObj = new AiModelProvider();
        updateObj.setId(id);
        updateObj.setStatus(status);
        aiModelProviderService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模型供应商")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:delete')")
    public CommonResult<Boolean> deleteAiModelProvider(@RequestParam("id") Long id) {
        // 实体未标注 @TableLogic，removeById 会物理删除，这里显式做逻辑删除
        aiModelProviderService.update(new LambdaUpdateWrapper<AiModelProvider>()
                .eq(AiModelProvider::getId, id)
                .set(AiModelProvider::getDeleted, 1));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除模型供应商")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:delete')")
    public CommonResult<Boolean> deleteAiModelProviderList(@RequestParam("ids") List<Long> ids) {
        aiModelProviderService.update(new LambdaUpdateWrapper<AiModelProvider>()
                .in(AiModelProvider::getId, ids)
                .set(AiModelProvider::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得模型供应商")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:query')")
    public CommonResult<AiModelProviderRespVO> getAiModelProvider(@RequestParam("id") Long id) {
        AiModelProvider provider = aiModelProviderService.getAiModelProvider(id);
        return success(toRespVO(provider));
    }

    @GetMapping("/page")
    @Operation(summary = "获得模型供应商分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:query')")
    public CommonResult<PageResult<AiModelProviderRespVO>> getAiModelProviderPage(@Valid AiModelProviderPageReqVO pageReqVO) {
        IPage<AiModelProvider> page = aiModelProviderService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiModelProvider> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        PageResult<AiModelProviderRespVO> voPage = BeanUtils.toBean(pageResult, AiModelProviderRespVO.class);
        if (voPage.getList() != null) {
            fillConfigured(voPage.getList(), page.getRecords());
        }
        return success(voPage);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用的模型供应商精简列表")
    public CommonResult<List<AiModelProviderSimpleRespVO>> getAiModelProviderSimpleList() {
        List<AiModelProvider> list = aiModelProviderService.list(new LambdaQueryWrapperX<AiModelProvider>()
                .eq(AiModelProvider::getStatus, 0)
                .eq(AiModelProvider::getDeleted, 0)
                .orderByAsc(AiModelProvider::getSort));
        return success(BeanUtils.toBean(list, AiModelProviderSimpleRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出模型供应商 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-model-provider:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiModelProviderExcel(@Valid AiModelProviderPageReqVO pageReqVO,
                                           HttpServletResponse response) throws IOException {
        List<AiModelProvider> list = aiModelProviderService.list(buildQuery(pageReqVO));
        List<AiModelProviderRespVO> vos = BeanUtils.toBean(list, AiModelProviderRespVO.class);
        fillConfigured(vos, list);
        ExcelUtils.write(response, "模型供应商.xls", "数据", AiModelProviderRespVO.class, vos);
    }

    /** 响应只带「是否已配置」状态位，不含任何凭证内容 */
    private AiModelProviderRespVO toRespVO(AiModelProvider provider) {
        if (provider == null) {
            return null;
        }
        AiModelProviderRespVO vo = BeanUtils.toBean(provider, AiModelProviderRespVO.class);
        vo.setConfigured(StringUtils.hasText(provider.getApiKey()));
        return vo;
    }

    private void fillConfigured(List<AiModelProviderRespVO> vos, List<AiModelProvider> entities) {
        if (vos == null || entities == null) {
            return;
        }
        Map<Long, Boolean> flags = entities.stream().collect(Collectors.toMap(
                AiModelProvider::getId, p -> StringUtils.hasText(p.getApiKey()), (a, b) -> a));
        for (AiModelProviderRespVO vo : vos) {
            vo.setConfigured(flags.getOrDefault(vo.getId(), Boolean.FALSE));
        }
    }

    private LambdaQueryWrapper<AiModelProvider> buildQuery(AiModelProviderPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiModelProvider>()
                .likeIfPresent(AiModelProvider::getName, reqVO.getName())
                .eqIfPresent(AiModelProvider::getCode, reqVO.getCode())
                .eqIfPresent(AiModelProvider::getProtocol, reqVO.getProtocol())
                .eqIfPresent(AiModelProvider::getStatus, reqVO.getStatus())
                .betweenIfPresent(AiModelProvider::getCreateTime, reqVO.getCreateTime())
                .eq(AiModelProvider::getDeleted, 0)
                .orderByAsc(AiModelProvider::getSort)
                .orderByDesc(AiModelProvider::getId);
    }

    /**
     * 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段
     */
    private void fillCreateFields(AiModelProvider entity) {
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
