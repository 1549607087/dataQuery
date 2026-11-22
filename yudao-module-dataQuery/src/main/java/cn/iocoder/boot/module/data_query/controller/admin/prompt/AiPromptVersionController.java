package cn.iocoder.boot.module.data_query.controller.admin.prompt;

import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version.AiPromptRollbackReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version.AiPromptVersionDiffRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version.AiPromptVersionPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.version.AiPromptVersionRespVO;
import cn.iocoder.boot.module.data_query.entity.AiPrompt;
import cn.iocoder.boot.module.data_query.entity.AiPromptVersion;
import cn.iocoder.boot.module.data_query.service.AiPromptService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 提示词版本（内容快照）
 *
 * 版本行由 /data-query/ai-prompt/publish 生成，管理端不开放增删改，保证快照不可篡改；
 * 回滚采用「基于历史版本生成新版本」的方式，不修改任何历史记录。
 */
@Tag(name = "管理后台 - 提示词版本")
@RestController
@RequestMapping("/data-query/ai-prompt-version")
@Validated
public class AiPromptVersionController {

    @Resource
    private AiPromptVersionService aiPromptVersionService;
    @Resource
    private AiPromptService aiPromptService;

    @GetMapping("/get")
    @Operation(summary = "获得提示词版本详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-version:query')")
    public CommonResult<AiPromptVersionRespVO> getAiPromptVersion(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(getVersion(id), AiPromptVersionRespVO.class));
    }

    @GetMapping("/list-by-prompt")
    @Operation(summary = "获得某条提示词的全部版本（时间线）")
    @Parameter(name = "promptId", description = "提示词编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-version:query')")
    public CommonResult<List<AiPromptVersionRespVO>> getAiPromptVersionListByPrompt(
            @RequestParam("promptId") Long promptId) {
        List<AiPromptVersion> list = aiPromptVersionService.list(new LambdaQueryWrapperX<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptId, promptId)
                .eq(AiPromptVersion::getDeleted, 0)
                .orderByDesc(AiPromptVersion::getVersion));
        return success(BeanUtils.toBean(list, AiPromptVersionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得提示词版本分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-version:query')")
    public CommonResult<PageResult<AiPromptVersionRespVO>> getAiPromptVersionPage(@Valid AiPromptVersionPageReqVO pageReqVO) {
        IPage<AiPromptVersion> page = aiPromptVersionService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiPromptVersion> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiPromptVersionRespVO.class));
    }

    @GetMapping("/diff")
    @Operation(summary = "对比两个提示词版本的正文与变量定义")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-version:query')")
    public CommonResult<AiPromptVersionDiffRespVO> diffAiPromptVersion(
            @RequestParam("sourceId") @Parameter(name = "sourceId", description = "源版本编号", required = true) Long sourceId,
            @RequestParam("targetId") @Parameter(name = "targetId", description = "目标版本编号", required = true) Long targetId) {
        AiPromptVersion source = getVersion(sourceId);
        AiPromptVersion target = getVersion(targetId);
        if (!Objects.equals(source.getPromptId(), target.getPromptId())) {
            throw ServiceExceptionUtil.invalidParamException("只能对比同一条提示词下的版本");
        }

        AiPromptVersionDiffRespVO respVO = new AiPromptVersionDiffRespVO();
        respVO.setSourceVersionId(source.getId());
        respVO.setTargetVersionId(target.getId());
        respVO.setSourceVersion(source.getVersion());
        respVO.setTargetVersion(target.getVersion());
        respVO.setSourceContent(source.getContent());
        respVO.setTargetContent(target.getContent());
        respVO.setSourceVariableDefs(source.getVariableDefs());
        respVO.setTargetVariableDefs(target.getVariableDefs());
        respVO.setContentChanged(!Objects.equals(source.getContent(), target.getContent()));
        respVO.setVariableChanged(!Objects.equals(source.getVariableDefs(), target.getVariableDefs()));
        return success(respVO);
    }

    @PutMapping("/rollback")
    @Operation(summary = "回滚提示词到指定版本（生成新版本，不改写历史）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-version:update')")
    public CommonResult<Long> rollbackAiPromptVersion(@Valid @RequestBody AiPromptRollbackReqVO rollbackReqVO) {
        AiPrompt prompt = getPrompt(rollbackReqVO.getPromptId());
        AiPromptVersion target = getVersion(rollbackReqVO.getVersionId());
        if (!Objects.equals(target.getPromptId(), prompt.getId())) {
            throw ServiceExceptionUtil.invalidParamException("目标版本不属于该提示词模板");
        }

        // 1. 基于目标版本内容生成新版本，版本号保持单调递增
        int nextVersion = nextVersion(prompt.getId());
        String changeLog = rollbackReqVO.getChangeLog() == null || rollbackReqVO.getChangeLog().isEmpty()
                ? "回滚至 v" + target.getVersion() : rollbackReqVO.getChangeLog();
        AiPromptVersion newVersion = new AiPromptVersion();
        newVersion.setPromptId(prompt.getId());
        newVersion.setVersion(nextVersion);
        newVersion.setContent(target.getContent());
        newVersion.setVariableDefs(target.getVariableDefs());
        newVersion.setParams(target.getParams());
        newVersion.setChangeLog(changeLog);
        newVersion.setContentHash(target.getContentHash());
        newVersion.setTokenEstimate(target.getTokenEstimate());
        newVersion.setStatus(1); // 已发布
        newVersion.setPublishTime(LocalDateTime.now());
        newVersion.setPublisher(currentUser());
        fillCreateFields(newVersion);
        aiPromptVersionService.save(newVersion);

        // 2. 原已发布版本归档
        aiPromptVersionService.update(new LambdaUpdateWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptId, prompt.getId())
                .ne(AiPromptVersion::getId, newVersion.getId())
                .eq(AiPromptVersion::getStatus, 1)
                .set(AiPromptVersion::getStatus, 3));

        // 3. 主表回写到目标版本内容（用 update wrapper，确保 null 值也能覆盖）
        aiPromptService.update(new LambdaUpdateWrapper<AiPrompt>()
                .eq(AiPrompt::getId, prompt.getId())
                .set(AiPrompt::getContent, target.getContent())
                .set(AiPrompt::getVariableDefs, target.getVariableDefs())
                .set(AiPrompt::getParams, target.getParams())
                .set(AiPrompt::getVersion, nextVersion)
                .set(AiPrompt::getCurrentVersionId, newVersion.getId())
                .set(AiPrompt::getUpdater, currentUser())
                .set(AiPrompt::getUpdateTime, LocalDateTime.now()));
        return success(newVersion.getId());
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出提示词版本 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-prompt-version:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiPromptVersionExcel(@Valid AiPromptVersionPageReqVO pageReqVO,
                                           HttpServletResponse response) throws IOException {
        List<AiPromptVersion> list = aiPromptVersionService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "提示词版本.xls", "数据", AiPromptVersionRespVO.class,
                BeanUtils.toBean(list, AiPromptVersionRespVO.class));
    }

    private LambdaQueryWrapper<AiPromptVersion> buildQuery(AiPromptVersionPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiPromptVersion>()
                .eqIfPresent(AiPromptVersion::getPromptId, reqVO.getPromptId())
                .eqIfPresent(AiPromptVersion::getVersion, reqVO.getVersion())
                .eqIfPresent(AiPromptVersion::getStatus, reqVO.getStatus())
                .likeIfPresent(AiPromptVersion::getPublisher, reqVO.getPublisher())
                .betweenIfPresent(AiPromptVersion::getCreateTime, reqVO.getCreateTime())
                .eq(AiPromptVersion::getDeleted, 0)
                .orderByDesc(AiPromptVersion::getVersion);
    }

    private AiPrompt getPrompt(Long id) {
        AiPrompt prompt = aiPromptService.getAiPrompt(id);
        if (prompt == null) {
            throw ServiceExceptionUtil.invalidParamException("提示词模板不存在，编号：{}", id);
        }
        return prompt;
    }

    private AiPromptVersion getVersion(Long id) {
        AiPromptVersion version = aiPromptVersionService.getAiPromptVersion(id);
        if (version == null) {
            throw ServiceExceptionUtil.invalidParamException("提示词版本不存在，编号：{}", id);
        }
        return version;
    }

    private int nextVersion(Long promptId) {
        List<AiPromptVersion> versions = aiPromptVersionService.list(new LambdaQueryWrapperX<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptId, promptId)
                .eq(AiPromptVersion::getDeleted, 0));
        return versions.stream()
                .mapToInt(v -> v.getVersion() == null ? 0 : v.getVersion())
                .max().orElse(0) + 1;
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

    /** 实体为普通 POJO（未继承 BaseDO），框架的 DefaultDBFieldHandler 不会自动填充，故显式补齐审计字段 */
    private void fillCreateFields(AiPromptVersion entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 MyBatis Plus 的 ASSIGN_ID 生成
        entity.setCreator(currentUser());
        entity.setUpdater(currentUser());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

}
