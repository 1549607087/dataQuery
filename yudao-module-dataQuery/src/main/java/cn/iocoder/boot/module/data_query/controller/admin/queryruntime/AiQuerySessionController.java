package cn.iocoder.boot.module.data_query.controller.admin.queryruntime;

import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.session.AiQuerySessionPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.session.AiQuerySessionRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.session.AiQuerySessionSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.service.AiQuerySessionService;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 问数会话
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId。
 */
@Tag(name = "管理后台 - 问数会话")
@RestController
@RequestMapping("/data-query/ai-query-session")
@Validated
public class AiQuerySessionController {

    @Resource
    private AiQuerySessionService aiQuerySessionService;

    @PostMapping("/create")
    @Operation(summary = "创建问数会话")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:create')")
    public CommonResult<Long> createAiQuerySession(@Valid @RequestBody AiQuerySessionSaveReqVO reqVO) {
        return success(aiQuerySessionService.createAiQuerySession(BeanUtils.toBean(reqVO, AiQuerySession.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新问数会话")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:update')")
    public CommonResult<Boolean> updateAiQuerySession(@Valid @RequestBody AiQuerySessionSaveReqVO reqVO) {
        aiQuerySessionService.updateAiQuerySession(BeanUtils.toBean(reqVO, AiQuerySession.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除问数会话")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:delete')")
    public CommonResult<Boolean> deleteAiQuerySession(@RequestParam("id") Long id) {
        aiQuerySessionService.deleteAiQuerySession(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除问数会话")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:delete')")
    public CommonResult<Boolean> deleteAiQuerySessionList(@RequestParam("ids") List<Long> ids) {
        aiQuerySessionService.deleteAiQuerySessionList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得问数会话")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:query')")
    public CommonResult<AiQuerySessionRespVO> getAiQuerySession(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiQuerySessionService.getAiQuerySession(id), AiQuerySessionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得问数会话分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:query')")
    public CommonResult<PageResult<AiQuerySessionRespVO>> getAiQuerySessionPage(@Valid AiQuerySessionPageReqVO pageReqVO) {
        IPage<AiQuerySession> page = aiQuerySessionService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiQuerySession> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiQuerySessionRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出问数会话 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-session:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiQuerySessionExcel(@Valid AiQuerySessionPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiQuerySession> list = aiQuerySessionService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "问数会话.xls", "数据", AiQuerySessionRespVO.class,
                BeanUtils.toBean(list, AiQuerySessionRespVO.class));
    }

    private LambdaQueryWrapper<AiQuerySession> buildQuery(AiQuerySessionPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiQuerySession>()
                .eqIfPresent(AiQuerySession::getUserId, reqVO.getUserId())
                .eqIfPresent(AiQuerySession::getDatasourceId, reqVO.getDatasourceId())
                .likeIfPresent(AiQuerySession::getSessionNo, reqVO.getSessionNo())
                .eqIfPresent(AiQuerySession::getSceneCode, reqVO.getSceneCode())
                .eqIfPresent(AiQuerySession::getStatus, reqVO.getStatus())
                .likeIfPresent(AiQuerySession::getTitle, reqVO.getTitle())
                .eqIfPresent(AiQuerySession::getTraceId, reqVO.getTraceId())
                .betweenIfPresent(AiQuerySession::getCreateTime, reqVO.getCreateTime())
                .eq(AiQuerySession::getDeleted, 0)
                .orderByDesc(AiQuerySession::getId);
    }
}
