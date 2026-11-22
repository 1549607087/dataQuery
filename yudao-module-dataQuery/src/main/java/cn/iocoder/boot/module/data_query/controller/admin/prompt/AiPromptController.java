package cn.iocoder.boot.module.data_query.controller.admin.prompt;

import cn.hutool.json.JSONUtil;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt.*;
import cn.iocoder.boot.module.data_query.entity.AiPrompt;
import cn.iocoder.boot.module.data_query.entity.AiPromptVariable;
import cn.iocoder.boot.module.data_query.entity.AiPromptVersion;
import cn.iocoder.boot.module.data_query.service.AiPromptService;
import cn.iocoder.boot.module.data_query.service.AiPromptVariableService;
import cn.iocoder.boot.module.data_query.service.AiPromptVersionService;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 提示词模板（按场景分类、模板变量与占位符、版本发布）
 *
 * 说明：当前 Service 为 MyBatis Plus 通用 IService，故分页条件、逻辑删除、占位符校验、
 * 版本快照在 Controller 内实现；后续 Service 补齐自定义方法后可原样下沉，接口形态不变。
 */
@Tag(name = "管理后台 - 提示词模板")
@RestController
@RequestMapping("/data-query/ai-prompt")
@Validated
public class AiPromptController {

    /** 占位符统一 {{var_name}}，避免与 SQL 文本、SpEL 的 ${...} 冲突 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}");

    @Resource
    private AiPromptService aiPromptService;
    @Resource
    private AiPromptVersionService aiPromptVersionService;
    @Resource
    private AiPromptVariableService aiPromptVariableService;

    // ==================== 基础 CRUD ====================

    @PostMapping("/create")
    @Operation(summary = "创建提示词模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:create')")
    public CommonResult<Long> createAiPrompt(@Valid @RequestBody AiPromptSaveReqVO createReqVO) {
        AiPrompt prompt = BeanUtils.toBean(createReqVO, AiPrompt.class);
        fillCreateFields(prompt);
        aiPromptService.save(prompt);
        return success(prompt.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新提示词模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:update')")
    public CommonResult<Boolean> updateAiPrompt(@Valid @RequestBody AiPromptSaveReqVO updateReqVO) {
        AiPrompt updateObj = BeanUtils.toBean(updateReqVO, AiPrompt.class);
        fillUpdateFields(updateObj);
        aiPromptService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改提示词模板状态")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:update')")
    public CommonResult<Boolean> updateAiPromptStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 启用、1 停用、2 灰度", required = true) Integer status) {
        AiPrompt updateObj = new AiPrompt();
        updateObj.setId(id);
        updateObj.setStatus(status);
        fillUpdateFields(updateObj);
        aiPromptService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除提示词模板（级联逻辑删除变量与版本）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:delete')")
    public CommonResult<Boolean> deleteAiPrompt(@RequestParam("id") Long id) {
        deleteCascade(Collections.singletonList(id));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除提示词模板")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:delete')")
    public CommonResult<Boolean> deleteAiPromptList(@RequestParam("ids") List<Long> ids) {
        deleteCascade(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得提示词模板")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:query')")
    public CommonResult<AiPromptRespVO> getAiPrompt(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(getPrompt(id), AiPromptRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得提示词模板分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:query')")
    public CommonResult<PageResult<AiPromptRespVO>> getAiPromptPage(@Valid AiPromptPageReqVO pageReqVO) {
        IPage<AiPrompt> page = aiPromptService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiPrompt> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiPromptRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用的提示词模板精简列表")
    public CommonResult<List<AiPromptSimpleRespVO>> getAiPromptSimpleList(
            @RequestParam(value = "sceneCode", required = false)
            @Parameter(name = "sceneCode", description = "按场景编码过滤，不传则返回全部") String sceneCode) {
        List<AiPrompt> list = aiPromptService.list(new LambdaQueryWrapperX<AiPrompt>()
                .eqIfPresent(AiPrompt::getSceneCode, sceneCode)
                .eq(AiPrompt::getStatus, 0)
                .eq(AiPrompt::getDeleted, 0)
                .orderByAsc(AiPrompt::getSort));
        return success(BeanUtils.toBean(list, AiPromptSimpleRespVO.class));
    }

    @GetMapping("/get-by-scene")
    @Operation(summary = "按场景编码获得当前生效的提示词模板")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:query')")
    public CommonResult<AiPromptRespVO> getAiPromptByScene(
            @RequestParam("sceneCode") @Parameter(name = "sceneCode", description = "场景编码", required = true, example = "SQL_GEN") String sceneCode) {
        // 租户隔离由框架多租户拦截器自动追加 tenant_id 条件；同场景内按 sort 取优先级最高的启用模板
        List<AiPrompt> list = aiPromptService.list(new LambdaQueryWrapperX<AiPrompt>()
                .eq(AiPrompt::getSceneCode, sceneCode)
                .eq(AiPrompt::getStatus, 0)
                .eq(AiPrompt::getDeleted, 0)
                .orderByAsc(AiPrompt::getSort));
        AiPrompt prompt = list.isEmpty() ? null : list.get(0);
        return success(BeanUtils.toBean(prompt, AiPromptRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出提示词模板 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiPromptExcel(@Valid AiPromptPageReqVO pageReqVO,
                                    HttpServletResponse response) throws IOException {
        List<AiPrompt> list = aiPromptService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "提示词模板.xls", "数据", AiPromptRespVO.class,
                BeanUtils.toBean(list, AiPromptRespVO.class));
    }

    // ==================== 版本与复制 ====================

    @PostMapping("/publish")
    @Operation(summary = "发布提示词（生成版本快照并切换当前版本）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:update')")
    public CommonResult<Long> publishAiPrompt(@Valid @RequestBody AiPromptPublishReqVO publishReqVO) {
        AiPrompt prompt = getPrompt(publishReqVO.getId());

        // 1. 由变量定义编辑态生成只读快照
        List<AiPromptVariable> variables = listVariables(prompt.getId());
        String variableDefs = buildVariableDefs(variables);

        // 2. 版本号单调递增
        List<AiPromptVersion> versions = aiPromptVersionService.list(new LambdaQueryWrapperX<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptId, prompt.getId())
                .eq(AiPromptVersion::getDeleted, 0));
        int nextVersion = versions.stream()
                .mapToInt(v -> v.getVersion() == null ? 0 : v.getVersion())
                .max().orElse(0) + 1;
        nextVersion = Math.max(nextVersion, (prompt.getVersion() == null ? 0 : prompt.getVersion()) + 1);

        // 3. 写入版本快照
        AiPromptVersion version = new AiPromptVersion();
        version.setPromptId(prompt.getId());
        version.setVersion(nextVersion);
        version.setContent(prompt.getContent());
        version.setVariableDefs(variableDefs);
        version.setParams(prompt.getParams());
        version.setChangeLog(publishReqVO.getChangeLog());
        version.setContentHash(sha256(prompt.getContent() == null ? "" : prompt.getContent()));
        version.setTokenEstimate(estimateTokens(prompt.getContent()));
        version.setStatus(1); // 已发布
        version.setPublishTime(LocalDateTime.now());
        version.setPublisher(currentUser());
        fillVersionCreateFields(version);
        aiPromptVersionService.save(version);

        // 4. 主表指向新版本，并把旧版本标记为归档
        aiPromptVersionService.update(new LambdaUpdateWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptId, prompt.getId())
                .ne(AiPromptVersion::getId, version.getId())
                .eq(AiPromptVersion::getStatus, 1)
                .set(AiPromptVersion::getStatus, 3));
        AiPrompt updateObj = new AiPrompt();
        updateObj.setId(prompt.getId());
        updateObj.setVariableDefs(variableDefs);
        updateObj.setVersion(nextVersion);
        updateObj.setCurrentVersionId(version.getId());
        fillUpdateFields(updateObj);
        aiPromptService.updateById(updateObj);
        return success(version.getId());
    }

    @PostMapping("/copy")
    @Operation(summary = "复制提示词模板（含变量定义）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:create')")
    public CommonResult<Long> copyAiPrompt(@Valid @RequestBody AiPromptCopyReqVO copyReqVO) {
        AiPrompt source = getPrompt(copyReqVO.getId());
        AiPrompt copy = BeanUtils.toBean(source, AiPrompt.class);
        copy.setName(copyReqVO.getName());
        copy.setCode(copyReqVO.getCode());
        copy.setSource(3);              // 复制自平台模板
        copy.setParentId(source.getId());
        copy.setVersion(1);
        copy.setCurrentVersionId(0L);
        copy.setStatus(1);              // 复制件默认停用，发布后再启用
        fillCreateFields(copy);
        aiPromptService.save(copy);

        // 变量定义一并复制；变量快照待发布时生成
        for (AiPromptVariable variable : listVariables(source.getId())) {
            AiPromptVariable variableCopy = BeanUtils.toBean(variable, AiPromptVariable.class);
            variableCopy.setPromptId(copy.getId());
            fillVariableCreateFields(variableCopy);
            aiPromptVariableService.save(variableCopy);
        }
        return success(copy.getId());
    }

    // ==================== 模板变量与占位符 ====================

    @GetMapping("/validate")
    @Operation(summary = "校验模板正文与占位符定义是否一致")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:query')")
    public CommonResult<AiPromptValidateRespVO> validateAiPrompt(@RequestParam("id") Long id) {
        AiPrompt prompt = getPrompt(id);
        List<AiPromptVariable> variables = listVariables(id);

        Set<String> contentVariables = extractVariables(prompt.getContent());
        Set<String> declaredVariables = variables.stream()
                .map(AiPromptVariable::getVarName)
                .filter(name -> StringUtils.hasText(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> undeclared = new LinkedHashSet<>(contentVariables);
        undeclared.removeAll(declaredVariables);
        Set<String> unused = new LinkedHashSet<>(declaredVariables);
        unused.removeAll(contentVariables);
        List<String> suspicious = variables.stream()
                .filter(v -> Objects.equals(v.getRequired(), 1)
                        && !StringUtils.hasText(v.getDefaultValue())
                        && !StringUtils.hasText(v.getSourceKey()))
                .map(AiPromptVariable::getVarName)
                .collect(Collectors.toList());

        AiPromptValidateRespVO respVO = new AiPromptValidateRespVO();
        respVO.setValid(undeclared.isEmpty());
        respVO.setContentVariables(new ArrayList<>(contentVariables));
        respVO.setDeclaredVariables(new ArrayList<>(declaredVariables));
        respVO.setUndeclaredVariables(new ArrayList<>(undeclared));
        respVO.setUnusedVariables(new ArrayList<>(unused));
        respVO.setSuspiciousVariables(suspicious);
        return success(respVO);
    }

    @PostMapping("/render")
    @Operation(summary = "渲染调试提示词模板（填充占位符）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt:query')")
    public CommonResult<AiPromptRenderRespVO> renderAiPrompt(@Valid @RequestBody AiPromptRenderReqVO renderReqVO) {
        // 1. 确定正文：优先使用入参 content（编辑态试渲染），否则按 promptId 读库
        String content = renderReqVO.getContent();
        Long promptId = renderReqVO.getPromptId();
        if (!StringUtils.hasText(content)) {
            if (promptId == null) {
                throw ServiceExceptionUtil.invalidParamException("模板正文与提示词编号不能同时为空");
            }
            content = getPrompt(promptId).getContent();
        }

        // 2. 合并变量值：显式传入 > 变量定义默认值
        List<AiPromptVariable> variables = promptId == null
                ? Collections.emptyList() : listVariables(promptId);
        Map<String, AiPromptVariable> declared = new LinkedHashMap<>();
        for (AiPromptVariable variable : variables) {
            if (StringUtils.hasText(variable.getVarName())) {
                declared.put(variable.getVarName(), variable);
            }
        }
        Map<String, String> values = renderReqVO.getVariables() == null
                ? new HashMap<>() : new HashMap<>(renderReqVO.getVariables());
        for (Map.Entry<String, AiPromptVariable> entry : declared.entrySet()) {
            if (!values.containsKey(entry.getKey()) && StringUtils.hasText(entry.getValue().getDefaultValue())) {
                values.put(entry.getKey(), entry.getValue().getDefaultValue());
            }
        }

        // 3. 校验占位符登记情况与必填值
        Set<String> contentVariables = extractVariables(content);
        Set<String> undeclared = new LinkedHashSet<>(contentVariables);
        undeclared.removeAll(declared.keySet());
        Set<String> unused = new LinkedHashSet<>(declared.keySet());
        unused.removeAll(contentVariables);
        List<String> missing = new ArrayList<>();
        for (String name : contentVariables) {
            AiPromptVariable variable = declared.get(name);
            boolean required = variable != null && Objects.equals(variable.getRequired(), 1);
            if (required && !values.containsKey(name)) {
                missing.add(name);
            }
        }
        if (Boolean.TRUE.equals(renderReqVO.getStrict()) && !missing.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("缺少必填占位符的值：{}", String.join("、", missing));
        }

        // 4. 渲染（未提供的占位符保留原文，便于调试台直观看到遗漏）
        String rendered = renderContent(content, values);
        AiPromptRenderRespVO respVO = new AiPromptRenderRespVO();
        respVO.setContent(rendered);
        respVO.setVariables(new ArrayList<>(contentVariables));
        respVO.setUndeclaredVariables(new ArrayList<>(undeclared));
        respVO.setUnusedVariables(new ArrayList<>(unused));
        respVO.setMissingVariables(missing);
        respVO.setValid(undeclared.isEmpty() && missing.isEmpty());
        respVO.setTokenEstimate(estimateTokens(rendered));
        return success(respVO);
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<AiPrompt> buildQuery(AiPromptPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiPrompt>()
                .eqIfPresent(AiPrompt::getSceneCode, reqVO.getSceneCode())
                .likeIfPresent(AiPrompt::getName, reqVO.getName())
                .likeIfPresent(AiPrompt::getCode, reqVO.getCode())
                .eqIfPresent(AiPrompt::getPromptType, reqVO.getPromptType())
                .eqIfPresent(AiPrompt::getStatus, reqVO.getStatus())
                .eqIfPresent(AiPrompt::getSource, reqVO.getSource())
                .eqIfPresent(AiPrompt::getModelId, reqVO.getModelId())
                .betweenIfPresent(AiPrompt::getCreateTime, reqVO.getCreateTime())
                .eq(AiPrompt::getDeleted, 0)
                .orderByAsc(AiPrompt::getSort)
                .orderByDesc(AiPrompt::getId);
    }

    private AiPrompt getPrompt(Long id) {
        AiPrompt prompt = aiPromptService.getAiPrompt(id);
        if (prompt == null) {
            throw ServiceExceptionUtil.invalidParamException("提示词模板不存在，编号：{}", id);
        }
        return prompt;
    }

    private List<AiPromptVariable> listVariables(Long promptId) {
        return aiPromptVariableService.list(new LambdaQueryWrapperX<AiPromptVariable>()
                .eq(AiPromptVariable::getPromptId, promptId)
                .eq(AiPromptVariable::getDeleted, 0)
                .orderByAsc(AiPromptVariable::getSort));
    }

    private void deleteCascade(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        aiPromptService.update(new LambdaUpdateWrapper<AiPrompt>()
                .in(AiPrompt::getId, ids)
                .set(AiPrompt::getDeleted, 1));
        aiPromptVariableService.update(new LambdaUpdateWrapper<AiPromptVariable>()
                .in(AiPromptVariable::getPromptId, ids)
                .set(AiPromptVariable::getDeleted, 1));
        aiPromptVersionService.update(new LambdaUpdateWrapper<AiPromptVersion>()
                .in(AiPromptVersion::getPromptId, ids)
                .set(AiPromptVersion::getDeleted, 1));
    }

    /** 由变量定义编辑态生成发布用的只读 JSON 快照 */
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

    private Set<String> extractVariables(String content) {
        Set<String> result = new LinkedHashSet<>();
        if (!StringUtils.hasText(content)) {
            return result;
        }
        Matcher matcher = VAR_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private String renderContent(String content, Map<String, String> values) {
        Matcher matcher = VAR_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token 粗略预估：中文按字符、英文按 4 字符 1 token，仅用于调试台展示 */
    private int estimateTokens(String content) {
        return content == null ? 0 : content.length() / 4;
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

    /** 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段 */
    private void fillCreateFields(AiPrompt entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 MyBatis Plus 的 ASSIGN_ID 生成
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        entity.setVersion(1);
        entity.setCurrentVersionId(0L);
        if (entity.getSource() == null) {
            entity.setSource(2); // 管理端新建视为租户自定义
        }
        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        if (entity.getModelId() == null) {
            entity.setModelId(0L);
        }
    }

    private void fillUpdateFields(AiPrompt entity) {
        entity.setUpdater(currentUser());
        entity.setUpdateTime(LocalDateTime.now());
    }

    private void fillVersionCreateFields(AiPromptVersion entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null);
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void fillVariableCreateFields(AiPromptVariable entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null);
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

}
