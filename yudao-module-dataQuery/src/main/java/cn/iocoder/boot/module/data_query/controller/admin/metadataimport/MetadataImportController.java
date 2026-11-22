package cn.iocoder.boot.module.data_query.controller.admin.metadataimport;

import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataImportReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataImportRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataPreviewRespVO;
import cn.iocoder.boot.module.data_query.service.MetadataImportService;
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
 * 管理后台 - 表结构一键导入
 *
 * 前端只需一个表单：填连接信息 → 点「预览」看将导入哪些表 → 点「一键导入」完成全部初始化。
 * 原有 /ai-datasource/create、/metadata-sync/sync、表与字段的单条 CRUD 全部保留，
 * 用于导入后的微调，不作为接入主链路。
 */
@Tag(name = "管理后台 - 表结构一键导入")
@RestController
@RequestMapping("/data-query/metadata-import")
@Validated
public class MetadataImportController {

    @Resource
    private MetadataImportService metadataImportService;

    @PostMapping("/preview")
    @Operation(summary = "一键导入预览：只读扫描目标库结构，不创建数据源、不写任何元数据行")
    @PreAuthorize("@ss.hasPermission('data-query:metadata-import:preview')")
    public CommonResult<MetadataPreviewRespVO> preview(@Valid @RequestBody MetadataImportReqVO reqVO) {
        return success(metadataImportService.preview(reqVO));
    }

    @PostMapping("/import")
    @Operation(summary = "一键导入：创建/复用数据源 → 测连接 → 全量表列导入 → 向量索引")
    @PreAuthorize("@ss.hasPermission('data-query:metadata-import:import')")
    public CommonResult<MetadataImportRespVO> importMetadata(@Valid @RequestBody MetadataImportReqVO reqVO) {
        return success(metadataImportService.importMetadata(reqVO));
    }

}
