package cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime;

import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderRespVO;
import cn.iocoder.boot.module.data_query.service.PromptRenderService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - S4 提示词运行时渲染（P3-B）
 *
 * 与 /data-query/ai-prompt/render（编辑态调试）的区别：
 * 本接口按场景锁定已发布的版本快照，是 P5 编排器使用的运行时入口。
 */
@Tag(name = "管理后台 - S4 提示词运行时渲染")
@RestController
@RequestMapping("/data-query/prompt-runtime")
@Validated
public class PromptRuntimeController {

    @Resource
    private PromptRenderService promptRenderService;

    @PostMapping("/render")
    @Operation(summary = "按场景锁定版本快照并渲染提示词（可选自动执行 S3 召回注入 schema）")
    @PreAuthorize("@ss.hasPermission('data-query:prompt-runtime:query')")
    public CommonResult<PromptRenderRespVO> render(@Valid @RequestBody PromptRenderReqVO reqVO) {
        return success(promptRenderService.render(reqVO));
    }

}
