package cn.iocoder.boot.module.data_query.controller.admin.knowledge;

import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeBasePageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeBaseRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeBaseSaveReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeBaseSimpleRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
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

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_CODE_DUPLICATE;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_NOT_EXISTS;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 知识库（RAG 独立问答旁路的顶层实体，租户私有）
 *
 * 说明：
 * 1. tenant_id 由 MyBatis-Plus 租户插件自动注入，接口不接收、不手动 set；
 * 2. 实体为普通 POJO（未继承 BaseDO），审计字段（creator/createTime 等）在此显式填充；
 * 3. 当前 Service 为通用 IService，分页条件、逻辑删除、code 唯一性校验在 Controller 内实现。
 */
@Tag(name = "管理后台 - 知识库")
@RestController
@RequestMapping("/data-query/knowledge-base")
@Validated
public class AiKnowledgeBaseController {

    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;

    @PostMapping("/create")
    @Operation(summary = "创建知识库")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:create')")
    public CommonResult<Long> createKnowledgeBase(@Valid @RequestBody AiKnowledgeBaseSaveReqVO createReqVO) {
        // 租户内 code 唯一
        validateCodeUnique(createReqVO.getCode(), null);
        AiKnowledgeBase entity = BeanUtils.toBean(createReqVO, AiKnowledgeBase.class);
        fillCreateFields(entity);
        knowledgeBaseService.save(entity);
        return success(entity.getId());
    }

    @PutMapping("/update")
    @Operation(summary = "更新知识库")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:update')")
    public CommonResult<Boolean> updateKnowledgeBase(@Valid @RequestBody AiKnowledgeBaseSaveReqVO updateReqVO) {
        // 校验存在（租户隔离由插件保证）
        getKnowledgeBaseOrThrow(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getCode(), updateReqVO.getId());
        AiKnowledgeBase updateObj = BeanUtils.toBean(updateReqVO, AiKnowledgeBase.class);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        knowledgeBaseService.updateById(updateObj);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "修改知识库状态")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:update')")
    public CommonResult<Boolean> updateKnowledgeBaseStatus(
            @RequestParam("id") @Parameter(name = "id", description = "编号", required = true) Long id,
            @RequestParam("status") @Parameter(name = "status", description = "状态：0 正常、1 停用", required = true) Integer status) {
        getKnowledgeBaseOrThrow(id);
        AiKnowledgeBase updateObj = new AiKnowledgeBase();
        updateObj.setId(id);
        updateObj.setStatus(status);
        updateObj.setUpdater(currentUser());
        updateObj.setUpdateTime(LocalDateTime.now());
        knowledgeBaseService.updateById(updateObj);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除知识库")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:delete')")
    public CommonResult<Boolean> deleteKnowledgeBase(@RequestParam("id") Long id) {
        getKnowledgeBaseOrThrow(id);
        // 逻辑删除；文档/切片的级联下线在对应接口实现（P9.1 仅知识库自身）
        knowledgeBaseService.update(new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, id)
                .set(AiKnowledgeBase::getDeleted, 1)
                .set(AiKnowledgeBase::getUpdater, currentUser())
                .set(AiKnowledgeBase::getUpdateTime, LocalDateTime.now()));
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除知识库")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:delete')")
    public CommonResult<Boolean> deleteKnowledgeBaseList(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return success(true);
        }
        knowledgeBaseService.update(new LambdaUpdateWrapper<AiKnowledgeBase>()
                .in(AiKnowledgeBase::getId, ids)
                .eq(AiKnowledgeBase::getDeleted, 0)
                .set(AiKnowledgeBase::getDeleted, 1)
                .set(AiKnowledgeBase::getUpdater, currentUser())
                .set(AiKnowledgeBase::getUpdateTime, LocalDateTime.now()));
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得知识库")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:query')")
    public CommonResult<AiKnowledgeBaseRespVO> getKnowledgeBase(@RequestParam("id") Long id) {
        AiKnowledgeBase entity = getKnowledgeBaseOrThrow(id);
        return success(BeanUtils.toBean(entity, AiKnowledgeBaseRespVO.class));
    }

    @GetMapping("/get-by-code")
    @Operation(summary = "按业务编码获得知识库")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:query')")
    public CommonResult<AiKnowledgeBaseRespVO> getKnowledgeBaseByCode(
            @RequestParam("code") @Parameter(name = "code", description = "业务编码", required = true) String code) {
        AiKnowledgeBase entity = knowledgeBaseService.getKnowledgeBaseByCode(code);
        return success(BeanUtils.toBean(entity, AiKnowledgeBaseRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得知识库分页")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:query')")
    public CommonResult<PageResult<AiKnowledgeBaseRespVO>> getKnowledgeBasePage(@Valid AiKnowledgeBasePageReqVO pageReqVO) {
        IPage<AiKnowledgeBase> page = knowledgeBaseService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiKnowledgeBase> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiKnowledgeBaseRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用状态的知识库精简列表")
    public CommonResult<List<AiKnowledgeBaseSimpleRespVO>> getKnowledgeBaseSimpleList() {
        List<AiKnowledgeBase> list = knowledgeBaseService.list(new LambdaQueryWrapperX<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getStatus, 0)
                .eq(AiKnowledgeBase::getDeleted, 0)
                .orderByDesc(AiKnowledgeBase::getId));
        return success(BeanUtils.toBean(list, AiKnowledgeBaseSimpleRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出知识库 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-base:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportKnowledgeBaseExcel(@Valid AiKnowledgeBasePageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        List<AiKnowledgeBase> list = knowledgeBaseService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "知识库.xls", "数据", AiKnowledgeBaseRespVO.class,
                BeanUtils.toBean(list, AiKnowledgeBaseRespVO.class));
    }

    /** 查询条件：租户隔离由 MyBatis-Plus 插件自动追加 tenant_id，此处只加业务条件 + 逻辑删除 */
    private LambdaQueryWrapper<AiKnowledgeBase> buildQuery(AiKnowledgeBasePageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiKnowledgeBase>()
                .likeIfPresent(AiKnowledgeBase::getName, reqVO.getName())
                .likeIfPresent(AiKnowledgeBase::getCode, reqVO.getCode())
                .eqIfPresent(AiKnowledgeBase::getStatus, reqVO.getStatus())
                .eqIfPresent(AiKnowledgeBase::getEmbeddingModelCode, reqVO.getEmbeddingModelCode())
                .betweenIfPresent(AiKnowledgeBase::getCreateTime, reqVO.getCreateTime())
                .eq(AiKnowledgeBase::getDeleted, 0)
                .orderByDesc(AiKnowledgeBase::getId);
    }

    /** code 在租户内唯一（部分唯一索引 uk_ai_knowledge_base_01）；excludeId 用于更新时排除自身 */
    private void validateCodeUnique(String code, Long excludeId) {
        AiKnowledgeBase exist = knowledgeBaseService.getKnowledgeBaseByCode(code);
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw exception(KNOWLEDGE_BASE_CODE_DUPLICATE);
        }
    }

    private AiKnowledgeBase getKnowledgeBaseOrThrow(Long id) {
        AiKnowledgeBase entity = knowledgeBaseService.getKnowledgeBase(id);
        if (entity == null) {
            throw exception(KNOWLEDGE_BASE_NOT_EXISTS);
        }
        return entity;
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }

    /** 实体未标注 @TableLogic，框架的 DefaultDBFieldHandler 也不会对普通 POJO 自动填充，故显式补齐审计字段 */
    private void fillCreateFields(AiKnowledgeBase entity) {
        LocalDateTime now = LocalDateTime.now();
        String user = currentUser();
        entity.setId(null); // 由 MyBatis Plus 的 ASSIGN_ID 生成
        entity.setCreator(user);
        entity.setUpdater(user);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entity.setDeleted(0);
        // tenantId 不设置：由 MyBatis-Plus 租户插件在 INSERT 时自动注入当前租户
    }

}
