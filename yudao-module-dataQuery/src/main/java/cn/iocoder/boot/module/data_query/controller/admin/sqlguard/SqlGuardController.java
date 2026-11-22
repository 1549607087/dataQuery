package cn.iocoder.boot.module.data_query.controller.admin.sqlguard;

import cn.iocoder.boot.module.data_query.controller.admin.sqlguard.vo.SqlGuardReqVO;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import cn.iocoder.boot.module.data_query.framework.sqlguard.SqlGuardResult;
import cn.iocoder.boot.module.data_query.service.ReadOnlyQueryService;
import cn.iocoder.boot.module.data_query.service.SqlGuardService;
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
 * 管理后台 - S6 SQL 静态校验 / S7 只读执行（P4）。
 *
 * 这两个是「闸门自检 / 手工编辑 SQL 后复用」端点，P5 的 ask 接口会直接复用
 * 同样的 Service，不会另开一条绕过闸门的执行路径。
 */
@Tag(name = "管理后台 - S6/S7 SQL 安全闸门")
@RestController
@RequestMapping("/data-query/sql-guard")
@Validated
public class SqlGuardController {

    @Resource
    private SqlGuardService sqlGuardService;

    @Resource
    private ReadOnlyQueryService readOnlyQueryService;

    @PostMapping("/validate")
    @Operation(summary = "仅校验不执行：返回 SafeSql 与逐项校验报告（供手工编辑 SQL 后复用 S6 闸门）")
    @PreAuthorize("@ss.hasPermission('data-query:sql-guard:validate')")
    public CommonResult<SqlGuardResult> validate(@Valid @RequestBody SqlGuardReqVO reqVO) {
        return success(sqlGuardService.validate(reqVO.getDatasourceId(), reqVO.getSql(),
                reqVO.getQuestion(), reqVO.getSchemaContext()));
    }

    @PostMapping("/execute")
    @Operation(summary = "校验通过后走独立只读连接执行：返回表格、执行统计与审计编号")
    @PreAuthorize("@ss.hasPermission('data-query:sql-guard:execute')")
    public CommonResult<QueryResult> execute(@Valid @RequestBody SqlGuardReqVO reqVO) {
        return success(readOnlyQueryService.execute(reqVO.getDatasourceId(), reqVO.getSql(),
                reqVO.getQuestion(), reqVO.getSchemaContext()));
    }

}
