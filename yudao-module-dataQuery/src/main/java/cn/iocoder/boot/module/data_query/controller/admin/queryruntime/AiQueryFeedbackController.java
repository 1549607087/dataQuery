package cn.iocoder.boot.module.data_query.controller.admin.queryruntime;

import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.feedback.AiQueryFeedbackPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.feedback.AiQueryFeedbackRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.queryruntime.vo.feedback.AiQueryFeedbackSaveReqVO;
import cn.iocoder.boot.module.data_query.entity.AiQueryFeedback;
import cn.iocoder.boot.module.data_query.service.AiQueryFeedbackService;
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
 * 管理后台 - 反馈
 *
 * 租户边界：查询由 TenantDatabaseInterceptor 追加 tenant_id；写入在 Service 内显式 setTenantId。
 */
@Tag(name = "管理后台 - 反馈")
@RestController
@RequestMapping("/data-query/ai-query-feedback")
@Validated
public class AiQueryFeedbackController {

    @Resource
    private AiQueryFeedbackService aiQueryFeedbackService;

    @PostMapping("/create")
    @Operation(summary = "创建反馈")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:create')")
    public CommonResult<Long> createAiQueryFeedback(@Valid @RequestBody AiQueryFeedbackSaveReqVO reqVO) {
        return success(aiQueryFeedbackService.createAiQueryFeedback(BeanUtils.toBean(reqVO, AiQueryFeedback.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新反馈")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:update')")
    public CommonResult<Boolean> updateAiQueryFeedback(@Valid @RequestBody AiQueryFeedbackSaveReqVO reqVO) {
        aiQueryFeedbackService.updateAiQueryFeedback(BeanUtils.toBean(reqVO, AiQueryFeedback.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除反馈")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:delete')")
    public CommonResult<Boolean> deleteAiQueryFeedback(@RequestParam("id") Long id) {
        aiQueryFeedbackService.deleteAiQueryFeedback(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除反馈")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:delete')")
    public CommonResult<Boolean> deleteAiQueryFeedbackList(@RequestParam("ids") List<Long> ids) {
        aiQueryFeedbackService.deleteAiQueryFeedbackList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得反馈")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:query')")
    public CommonResult<AiQueryFeedbackRespVO> getAiQueryFeedback(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(aiQueryFeedbackService.getAiQueryFeedback(id), AiQueryFeedbackRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得反馈分页")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:query')")
    public CommonResult<PageResult<AiQueryFeedbackRespVO>> getAiQueryFeedbackPage(@Valid AiQueryFeedbackPageReqVO pageReqVO) {
        IPage<AiQueryFeedback> page = aiQueryFeedbackService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiQueryFeedback> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiQueryFeedbackRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出反馈 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:ai-query-feedback:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportAiQueryFeedbackExcel(@Valid AiQueryFeedbackPageReqVO pageReqVO,
                                HttpServletResponse response) throws IOException {
        List<AiQueryFeedback> list = aiQueryFeedbackService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "反馈.xls", "数据", AiQueryFeedbackRespVO.class,
                BeanUtils.toBean(list, AiQueryFeedbackRespVO.class));
    }

    private LambdaQueryWrapper<AiQueryFeedback> buildQuery(AiQueryFeedbackPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiQueryFeedback>()
                .eqIfPresent(AiQueryFeedback::getAuditId, reqVO.getAuditId())
                .eqIfPresent(AiQueryFeedback::getSessionId, reqVO.getSessionId())
                .eqIfPresent(AiQueryFeedback::getUserId, reqVO.getUserId())
                .eqIfPresent(AiQueryFeedback::getRating, reqVO.getRating())
                .eqIfPresent(AiQueryFeedback::getFeedbackType, reqVO.getFeedbackType())
                .eqIfPresent(AiQueryFeedback::getReviewed, reqVO.getReviewed())
                .betweenIfPresent(AiQueryFeedback::getCreateTime, reqVO.getCreateTime())
                .eq(AiQueryFeedback::getDeleted, 0)
                .orderByDesc(AiQueryFeedback::getId);
    }
}
