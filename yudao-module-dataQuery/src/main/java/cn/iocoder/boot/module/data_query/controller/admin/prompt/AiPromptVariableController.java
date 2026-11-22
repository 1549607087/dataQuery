package cn.iocoder.boot.module.data_query.controller.admin.prompt;

import cn.hutool.json.JSONUtil;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable.AiPromptVariableBatchSaveReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable.AiPromptVariablePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable.AiPromptVariableRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.variable.AiPromptVariableSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiPrompt;
import cn.iocoder.boot.module.data_query.entity.AiPromptVariable;
import cn.iocoder.boot.module.data_query.service.AiPromptService;
import cn.iocoder.boot.module.data_query.service.AiPromptVariableService;
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
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 提示词模板变量（占位符定义）
 *
 * 变量定义是「编辑态」；批量保存与发布时会生成 ai_prompt.variable_defs / ai_prompt_version.variable_defs
 * 只读 JSON 快照，运行时按快照渲染。占位符一致性请使用 /data-query/ai-prompt/validate 接口校验。
 */
@Tag(name = "管理后台 - 提示词模板变量")
@RestController
@RequestMapping("/data-query/ai-prompt-variable")
@Validated
public class AiPromptVariableController {

    @Resource
    private AiPromptVariableService aiPromptVariableService;
    @Resource
    private AiPromptService aiPromptService;

    @PostMapping("/create")
    @Operation(summary = "创建提示词模板变量")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:create')")
    public CommonResult<Long> createAiPromptVariable(@Valid @RequestBody AiPromptVariableSaveReqVO createReqVO) {
        AiPromptVariable variable = BeanUtils.toBean(createReqVO, AiPromptVariable.class);
        fillCreateFields(variable);
        aiPromptVariableService.save(variable);
        return success(variable.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新提示词模板变量")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:update')")
    public CommonResult<Boolean> updateAiPromptVariable(@Valid @RequestBody AiPromptVariableSaveReqVO updateReqVO) {
        AiPromptVariable updateObj = BeanUtils.toBean(updateReqVO, AiPromptVariable.class);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiPromptVariableService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改提示词模板变量状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:update')")
    public CommonResult<Boolean> updateAiPromptVariableStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 启用、1 停用", required = true) Integer status) {
        AiPromptVariable updateObj = new AiPromptVariable();
        updateObj.setId(id);
        updateObj.setStatus(status);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        aiPromptVariableService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除提示词模板变量")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:delete')")
    public CommonResult<Boolean> deleteAiPromptVariable(@RequestParam("id") Long id) {
        aiPromptVariableService.update(new LambdaUpdateWrapper<AiPromptVariable>()
                .eq(AiPromptVariable::getId, id)
                .set(AiPromptVariable::getDeleted, 1));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除提示词模板变量")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:delete')")
    public CommonResult<Boolean> deleteAiPromptVariableList(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return success(true);
        }
        aiPromptVariableService.update(new LambdaUpdateWrapper<AiPromptVariable>()
                .in(AiPromptVariable::getId, ids)
                .set(AiPromptVariable::getDeleted, 1));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得提示词模板变量")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:query')")
    public CommonResult<AiPromptVariableRespVO> getAiPromptVariable(@RequestParam("id") Long id) {
        AiPromptVariable variable = aiPromptVariableService.getAiPromptVariable(id);
        return success(BeanUtils.toBean(variable, AiPromptVariableRespVO.class));
    }

    @GetMapping("/list-by-prompt")
    @Operation(summary = "获得某条提示词的全部占位符定义（按排序）")
    @Parameter(name = "promptId", description = "提示词编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:query')")
    public CommonResult<List<AiPromptVariableRespVO>> getAiPromptVariableListByPrompt(
            @RequestParam("promptId") Long promptId) {
        return success(BeanUtils.toBean(listByPrompt(promptId), AiPromptVariableRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得提示词模板变量分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:query')")
    public CommonResult<PageResult<AiPromptVariableRespVO>> getAiPromptVariablePage(@Valid AiPromptVariablePageReqVO pageReqVO) {
        IPage<AiPromptVariable> page = aiPromptVariableService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiPromptVariable> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiPromptVariableRespVO.class));
    }

    @PostMapping("/batch-save")
    @Operation(summary = "整包保存某条提示词的占位符定义，并回写变量快照")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:update')")
    public CommonResult<Boolean> batchSaveAiPromptVariable(@Valid @RequestBody AiPromptVariableBatchSaveReqVO batchReqVO) {
        Long promptId = batchReqVO.getPromptId();

        // 1. 逻辑删除该模板下的旧变量（部分唯一索引带 WHERE deleted = 0，删除后可重建同名变量）
        aiPromptVariableService.update(new LambdaUpdateWrapper<AiPromptVariable>()
                .eq(AiPromptVariable::getPromptId, promptId)
                .set(AiPromptVariable::getDeleted, 1));

        // 2. 写入新的变量定义
        List<AiPromptVariable> saved = new ArrayList<>();
        for (AiPromptVariableSaveReqVO item : batchReqVO.getVariables()) {
            AiPromptVariable variable = BeanUtils.toBean(item, AiPromptVariable.class);
            variable.setPromptId(promptId);
            if (!StringUtils.hasText(variable.getSceneCode())) {
                variable.setSceneCode(batchReqVO.getSceneCode());
            }
            fillCreateFields(variable);
            aiPromptVariableService.save(variable);
            saved.add(variable);
        }

        // 3. 回写主表只读快照，保证运行时渲染与编辑态一致（发布时会再打一次版本快照）
        aiPromptService.update(new LambdaUpdateWrapper<AiPrompt>()
                .eq(AiPrompt::getId, promptId)
                .set(AiPrompt::getVariableDefs, buildVariableDefs(saved))
                .set(AiPrompt::getUpdater, currentUser())
                .set(AiPrompt::getUpdateTime, LocalDateTime.now()));
        return success(true);
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出提示词模板变量 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-variable:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiPromptVariableExcel(@Valid AiPromptVariablePageReqVO pageReqVO,
                                            HttpServletResponse response) throws IOException {
        List<AiPromptVariable> list = aiPromptVariableService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "提示词模板变量.xls", "数据", AiPromptVariableRespVO.class,
                BeanUtils.toBean(list, AiPromptVariableRespVO.class));
    }

    private LambdaQueryWrapper<AiPromptVariable> buildQuery(AiPromptVariablePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiPromptVariable>()
                .eqIfPresent(AiPromptVariable::getPromptId, reqVO.getPromptId())
                .eqIfPresent(AiPromptVariable::getSceneCode, reqVO.getSceneCode())
                .likeIfPresent(AiPromptVariable::getVarName, reqVO.getVarName())
                .likeIfPresent(AiPromptVariable::getVarLabel, reqVO.getVarLabel())
                .eqIfPresent(AiPromptVariable::getVarType, reqVO.getVarType())
                .eqIfPresent(AiPromptVariable::getSourceType, reqVO.getSourceType())
                .eqIfPresent(AiPromptVariable::getRequired, reqVO.getRequired())
                .eqIfPresent(AiPromptVariable::getStatus, reqVO.getStatus())
                .betweenIfPresent(AiPromptVariable::getCreateTime, reqVO.getCreateTime())
                .eq(AiPromptVariable::getDeleted, 0)
                .orderByAsc(AiPromptVariable::getSort)
                .orderByDesc(AiPromptVariable::getId);
    }

    private List<AiPromptVariable> listByPrompt(Long promptId) {
        return aiPromptVariableService.list(new LambdaQueryWrapperX<AiPromptVariable>()
                .eq(AiPromptVariable::getPromptId, promptId)
                .eq(AiPromptVariable::getDeleted, 0)
                .orderByAsc(AiPromptVariable::getSort));
    }

    /** 与 AiPromptController#publish 的快照结构保持一致，后续可下沉到 Service 复用 */
    private String buildVariableDefs(List<AiPromptVariable> variables) {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (AiPromptVariable variable : variables) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", variable.getVarName());
            def.put("required", Objects.equals(variable.getRequired(), 1));
            def.put("type", variable.getVarType());
            def.put("sourceType", variable.getSourceType());
            def.put("sourceKey", variable.getSourceKey());
            def.put("defaultValue", variable.getDefaultValue());
            def.put("maxLength", variable.getMaxLength());
            defs.add(def);
        }
        return JSONUtil.toJsonStr(defs);
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

    /** 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段 */
    private void fillCreateFields(AiPromptVariable entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 MyBatis Plus 的 ASSIGN_ID 生成
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

}
