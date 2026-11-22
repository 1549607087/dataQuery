package cn.iocoder.boot.module.data_query.controller.admin.recall;

import cn.iocoder.boot.module.data_query.controller.admin.recall.vo.SchemaRecallReqVO;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.service.SchemaRecallService;
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
 * 管理后台 - S3 Schema 召回预览（P3）
 *
 * 这是「召回自检 / 联调预览」端点，返回完整的 {@link SchemaContext} 供 P5 编排器
 * 与回归用例检查命中记录、白名单与降级状态；正式的问数入口在 P5 的 ask 接口。
 *
 * 响应刻意不回显 tenant_id / deleted / embedding 向量字面量（见 SchemaContext 注释）。
 */
@Tag(name = "管理后台 - S3 Schema 召回")
@RestController
@RequestMapping("/data-query/schema-recall")
@Validated
public class SchemaRecallController {

    @Resource
    private SchemaRecallService schemaRecallService;

    @PostMapping("/preview")
    @Operation(summary = "召回预览：向量 + 关键词混合重排，返回 SchemaContext 与 S6 白名单")
    @PreAuthorize("@ss.hasPermission('data-query:schema-recall:query')")
    public CommonResult<SchemaContext> preview(@Valid @RequestBody SchemaRecallReqVO reqVO) {
        return success(schemaRecallService.recall(reqVO.getDatasourceId(), reqVO.getQuestion(), reqVO.getTopK()));
    }

}
