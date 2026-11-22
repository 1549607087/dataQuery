package cn.iocoder.boot.module.data_query.controller.admin.knowledge;

import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeDocumentPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeDocumentRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeDocumentSwitchReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeDocumentUploadReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeDocumentUploadRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeDocumentProducer;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeParseMessage;
import cn.iocoder.boot.module.data_query.framework.knowledge.KnowledgeProperties;
import cn.iocoder.boot.module.data_query.framework.knowledge.version.KnowledgeVersionUtil;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeChunkService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeDocumentService;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_DISABLED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_BASE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOCUMENT_FILE_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOCUMENT_FILE_TOO_LARGE;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOCUMENT_FILE_TYPE_NOT_ALLOWED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOCUMENT_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOCUMENT_UPLOAD_FAILED;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 知识库文档（P9.1）
 *
 * 上传接口只做到「文件存储 + document 落库 + 发解析 MQ」，立即返回 documentId；
 * 解析/切片/向量化由消费端异步完成，前端用 get 接口轮询 parse_status。
 * delete 在单事务内级联下线切片并维护知识库冗余计数（DDL 4.7）。
 * rebuild 重置状态并重新发解析消息（复用事务消息保证状态与消息一致）。
 */
@Tag(name = "管理后台 - 知识库文档")
@RestController
@RequestMapping("/data-query/knowledge-document")
@Validated
public class AiKnowledgeDocumentController {

    @Resource
    private AiKnowledgeDocumentService documentService;
    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;
    @Resource
    private AiKnowledgeChunkService chunkService;
    @Resource
    private KnowledgeDocumentProducer knowledgeDocumentProducer;
    @Resource
    private KnowledgeProperties knowledgeProperties;
    @Resource
    private FileService fileService;

    // ==================== 上传 ====================

    @PostMapping("/upload")
    @Operation(summary = "上传知识库文档（落库+发MQ，异步解析）")
    @Parameter(name = "baseId", description = "所属知识库编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:upload')")
    public CommonResult<AiKnowledgeDocumentUploadRespVO> upload(@Valid AiKnowledgeDocumentUploadReqVO reqVO) {
        // 1. 校验知识库存在且启用（租户隔离由插件保证）
        AiKnowledgeBase base = knowledgeBaseService.getKnowledgeBase(reqVO.getBaseId());
        if (base == null) {
            throw exception(KNOWLEDGE_BASE_NOT_EXISTS);
        }
        if (!Integer.valueOf(0).equals(base.getStatus())) {
            throw exception(KNOWLEDGE_BASE_DISABLED);
        }

        // 2. 文件校验：类型白名单 + 大小上限 + 非空
        MultipartFile file = reqVO.getFile();
        String fileName = file.getOriginalFilename();
        String fileType = extractExtension(fileName);
        List<String> allowed = knowledgeProperties.getAllowedFileTypes();
        if (!allowed.contains(fileType)) {
            throw exception(KNOWLEDGE_DOCUMENT_FILE_TYPE_NOT_ALLOWED);
        }
        long maxBytes = knowledgeProperties.getMaxFileSizeMb().longValue() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw exception(KNOWLEDGE_DOCUMENT_FILE_TOO_LARGE, knowledgeProperties.getMaxFileSizeMb());
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw exception(KNOWLEDGE_DOCUMENT_UPLOAD_FAILED);
        }
        if (content.length == 0) {
            throw exception(KNOWLEDGE_DOCUMENT_FILE_EMPTY);
        }

        // 3. docKey（逻辑文档标识）：显式传入优先，否则取规范化文件名（去路径/trim/小写）
        String docKey = KnowledgeVersionUtil.normalizeFileName(
                org.springframework.util.StringUtils.hasText(reqVO.getDocKey()) ? reqVO.getDocKey() : fileName);
        String contentHash = sha256Hex(content);

        // 4. 版本链入口：按 docKey 取当前生效版判重
        //    幂等 = 同 docKey 生效版 + 内容哈希未变；内容变了就升新版本（同一文档多次上传不再全局判重）
        AiKnowledgeDocument latest = documentService.getLatestByDocKey(reqVO.getBaseId(), docKey);
        if (latest != null && java.util.Objects.equals(latest.getContentHash(), contentHash)) {
            return success(buildUploadResp(latest.getId(), latest.getParseStatus(), true,
                    latest.getDocKey(), latest.getVersion(), latest.getIsLatest()));
        }
        // 版本号分配基线必须是「现存 deleted=0 的最大版本」，而不是「生效版」：
        // 唯一键 uk_ai_knowledge_document_01 约束 (tenant_id, base_id, doc_key, version) WHERE deleted=0。
        // 若只看生效版，删掉生效版后历史版本仍 deleted=0 占着旧版本号，新版本会从 1 重新起而撞重复键。
        // 同理，手动 switch-version 回滚到旧版后，也不能从回滚版本继续加，否则撞更高的现存版本。
        AiKnowledgeDocument versionBase = documentService.getLatestVersionByDocKey(reqVO.getBaseId(), docKey);
        int newVersion = versionBase == null ? AiKnowledgeDocument.VERSION_FIRST : versionBase.getVersion() + 1;
        Long previousDocumentId = versionBase == null ? null : versionBase.getId();

        // 5. 预生成 documentId：事务半消息 Key / 回查依据，同时用作存储文件名。
        //    原实现把 docKey 与原始文件名塞进存储路径，长中文文件名经 encodeUrlPath
        //    （UTF-8 每字节转 %XX，膨胀 3 倍）后 file_url 会超过 varchar(512)。
        //    改用纯数字 documentId 做文件名 + ASCII 目录，从根上消除 URL 编码膨胀；
        //    原始文件名仍存 file_name 字段用于展示，docKey 仍存 doc_key 字段用于版本判重。
        Long documentId = IdWorker.getId();
        String storeName = documentId + "." + fileType.toLowerCase(Locale.ROOT);
        String directory = "knowledge/" + reqVO.getBaseId() + "/v" + newVersion;
        String fileUrl = fileService.createFile(content, storeName, directory, file.getContentType());

        // 6. 租户上下文：tenantId 由事务半消息透传，落库由 MyBatis-Plus 插件注入
        Long tenantId = TenantContextHolder.getTenantId();
        AiKnowledgeDocument entity = buildDocument(documentId, reqVO.getBaseId(), tenantId, fileName,
                fileType, content.length, fileUrl, contentHash, docKey, newVersion);

        KnowledgeParseMessage message = KnowledgeParseMessage.builder()
                .documentId(documentId)
                .baseId(reqVO.getBaseId())
                .tenantId(tenantId)
                .fileUrl(fileUrl)
                .fileType(fileType)
                .parseMode(versionBase == null ? "FULL" : "INCREMENTAL")
                .docKey(docKey)
                .version(newVersion)
                .previousDocumentId(previousDocumentId)
                .build();

        // 7. 事务消息：发半消息 → 落 document → 提交；保证落库与消息可消费一致
        knowledgeDocumentProducer.send(message, () -> documentService.save(entity));
        return success(buildUploadResp(documentId, 0, false, docKey, newVersion, AiKnowledgeDocument.LATEST_NO));
    }

    // ==================== 查询 ====================

    @GetMapping("/get")
    @Operation(summary = "获得知识库文档（前端轮询 parse_status）")
    @Parameter(name = "id", description = "编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:query')")
    public CommonResult<AiKnowledgeDocumentRespVO> getDocument(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(getDocumentOrThrow(id), AiKnowledgeDocumentRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得知识库文档分页")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:query')")
    public CommonResult<PageResult<AiKnowledgeDocumentRespVO>> getDocumentPage(@Valid AiKnowledgeDocumentPageReqVO pageReqVO) {
        IPage<AiKnowledgeDocument> page = documentService.page(MyBatisUtils.buildPage(pageReqVO), buildQuery(pageReqVO));
        PageResult<AiKnowledgeDocument> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiKnowledgeDocumentRespVO.class));
    }

    @GetMapping("/versions")
    @Operation(summary = "列出某逻辑文档的全部版本（含生效标记/解析状态/切片数），按版本降序")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:query')")
    public CommonResult<List<AiKnowledgeDocumentRespVO>> listVersions(@RequestParam("baseId") Long baseId,
                                                                       @RequestParam("docKey") String docKey) {
        return success(BeanUtils.toBean(documentService.listVersions(baseId, docKey),
                AiKnowledgeDocumentRespVO.class));
    }

    @PostMapping("/switch-version")
    @Operation(summary = "切换当前生效版本（只切标记，零 Embedding 成本，可回滚旧版）")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:update')")
    public CommonResult<AiKnowledgeDocumentRespVO> switchVersion(
            @Valid @RequestBody AiKnowledgeDocumentSwitchReqVO reqVO) {
        AiKnowledgeDocument switched = documentService.switchVersion(
                reqVO.getBaseId(), reqVO.getDocKey(), reqVO.getVersion());
        return success(BeanUtils.toBean(switched, AiKnowledgeDocumentRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出知识库文档 Excel")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportDocumentExcel(@Valid AiKnowledgeDocumentPageReqVO pageReqVO, HttpServletResponse response) throws IOException {
        List<AiKnowledgeDocument> list = documentService.list(buildQuery(pageReqVO));
        ExcelUtils.write(response, "知识库文档.xls", "数据", AiKnowledgeDocumentRespVO.class,
                BeanUtils.toBean(list, AiKnowledgeDocumentRespVO.class));
    }

    // ==================== 删除 / 重建 ====================

    @DeleteMapping("/delete")
    @Operation(summary = "删除知识库文档（级联下线切片，维护知识库计数）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:delete')")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Boolean> deleteDocument(@RequestParam("id") Long id) {
        AiKnowledgeDocument doc = getDocumentOrThrow(id);
        Long baseId = doc.getBaseId();

        // 1. 文档置为已下线 + 逻辑删除
        documentService.update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, id)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .set(AiKnowledgeDocument::getParseStatus, 4)
                .set(AiKnowledgeDocument::getDeleted, 1)
                .set(AiKnowledgeDocument::getUpdater, currentUser())
                .set(AiKnowledgeDocument::getUpdateTime, LocalDateTime.now()));

        // 2. 切片级联下线（sync_status=4 + 逻辑删除），不再被召回
        long removedChunks = chunkService.count(new LambdaQueryWrapperX<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getDocumentId, id)
                .eq(AiKnowledgeChunk::getDeleted, 0));
        chunkService.update(new LambdaUpdateWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getDocumentId, id)
                .eq(AiKnowledgeChunk::getDeleted, 0)
                .set(AiKnowledgeChunk::getSyncStatus, 4)
                .set(AiKnowledgeChunk::getDeleted, 1)
                .set(AiKnowledgeChunk::getUpdater, currentUser())
                .set(AiKnowledgeChunk::getUpdateTime, LocalDateTime.now()));

        // 3. 维护知识库冗余计数（GREATEST 防负；租户插件自动加 tenant_id）
        knowledgeBaseService.update(new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, baseId)
                .setSql("document_count = GREATEST(document_count - 1, 0)")
                .setSql("chunk_count = GREATEST(chunk_count - {0}, 0)", removedChunks)
                .set(AiKnowledgeBase::getUpdater, currentUser())
                .set(AiKnowledgeBase::getUpdateTime, LocalDateTime.now()));
        return success(true);
    }

    @PostMapping("/rebuild")
    @Operation(summary = "重建知识库文档（重置状态并重发解析 MQ）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-document:update')")
    public CommonResult<Boolean> rebuildDocument(@RequestParam("id") Long id) {
        AiKnowledgeDocument doc = getDocumentOrThrow(id);
        // 知识库停用则不允许重建（重建会消耗 Embedding 成本）
        AiKnowledgeBase base = knowledgeBaseService.getKnowledgeBase(doc.getBaseId());
        if (base == null) {
            throw exception(KNOWLEDGE_BASE_NOT_EXISTS);
        }
        if (!Integer.valueOf(0).equals(base.getStatus())) {
            throw exception(KNOWLEDGE_BASE_DISABLED);
        }

        KnowledgeParseMessage message = KnowledgeParseMessage.builder()
                .documentId(doc.getId())
                .baseId(doc.getBaseId())
                .tenantId(TenantContextHolder.getTenantId())
                .fileUrl(doc.getFileUrl())
                .fileType(doc.getFileType())
                .parseMode("FULL")
                // rebuild 不升版本，但要带原版本元信息供消费端给新切片冗余版本字段
                .docKey(doc.getDocKey())
                .version(doc.getVersion() == null ? AiKnowledgeDocument.VERSION_FIRST : doc.getVersion())
                .previousDocumentId(null)
                .build();

        // 本地事务：旧切片下线 + 文档状态重置；与消息提交保持一致
        Runnable localTx = () -> {
            // 先统计待下线切片数，用于同步递减知识库冗余计数（否则重建后 chunk_count 会翻倍）
            long removedChunks = chunkService.count(new LambdaQueryWrapperX<AiKnowledgeChunk>()
                    .eq(AiKnowledgeChunk::getDocumentId, id)
                    .eq(AiKnowledgeChunk::getDeleted, 0));
            chunkService.update(new LambdaUpdateWrapper<AiKnowledgeChunk>()
                    .eq(AiKnowledgeChunk::getDocumentId, id)
                    .eq(AiKnowledgeChunk::getDeleted, 0)
                    .set(AiKnowledgeChunk::getSyncStatus, 4)
                    .set(AiKnowledgeChunk::getDeleted, 1)
                    .set(AiKnowledgeChunk::getUpdater, currentUser())
                    .set(AiKnowledgeChunk::getUpdateTime, LocalDateTime.now()));
            // 递减 base 的 chunk_count（与 delete 接口口径一致，GREATEST 防负）
            knowledgeBaseService.update(new LambdaUpdateWrapper<AiKnowledgeBase>()
                    .eq(AiKnowledgeBase::getId, doc.getBaseId())
                    .setSql("chunk_count = GREATEST(chunk_count - {0}, 0)", removedChunks)
                    .set(AiKnowledgeBase::getUpdater, currentUser())
                    .set(AiKnowledgeBase::getUpdateTime, LocalDateTime.now()));
            documentService.update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                    .eq(AiKnowledgeDocument::getId, id)
                    .set(AiKnowledgeDocument::getParseStatus, 0)
                    .set(AiKnowledgeDocument::getParseError, "")
                    .set(AiKnowledgeDocument::getChunkCount, 0)
                    .set(AiKnowledgeDocument::getCharCount, 0L)
                    .set(AiKnowledgeDocument::getEnabled, 1)
                    .set(AiKnowledgeDocument::getUpdater, currentUser())
                    .set(AiKnowledgeDocument::getUpdateTime, LocalDateTime.now()));
        };
        knowledgeDocumentProducer.send(message, localTx);
        return success(true);
    }

    // ==================== 私有方法 ====================

    /** 查询条件：租户隔离由插件自动追加，此处只加业务条件 + 逻辑删除 */
    private LambdaQueryWrapperX<AiKnowledgeDocument> buildQuery(AiKnowledgeDocumentPageReqVO reqVO) {
        return new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eqIfPresent(AiKnowledgeDocument::getBaseId, reqVO.getBaseId())
                .likeIfPresent(AiKnowledgeDocument::getFileName, reqVO.getFileName())
                .eqIfPresent(AiKnowledgeDocument::getDocKey, reqVO.getDocKey())
                .eqIfPresent(AiKnowledgeDocument::getVersion, reqVO.getVersion())
                .eqIfPresent(AiKnowledgeDocument::getFileType, reqVO.getFileType())
                .eqIfPresent(AiKnowledgeDocument::getParseStatus, reqVO.getParseStatus())
                .eqIfPresent(AiKnowledgeDocument::getEnabled, reqVO.getEnabled())
                .betweenIfPresent(AiKnowledgeDocument::getCreateTime, reqVO.getCreateTime())
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .orderByDesc(AiKnowledgeDocument::getId);
    }

    private AiKnowledgeDocument getDocumentOrThrow(Long id) {
        AiKnowledgeDocument doc = documentService.getDocument(id);
        if (doc == null) {
            throw exception(KNOWLEDGE_DOCUMENT_NOT_EXISTS);
        }
        return doc;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).trim().toUpperCase(Locale.ROOT);
    }

    private String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw exception(KNOWLEDGE_DOCUMENT_UPLOAD_FAILED);
        }
    }

    private AiKnowledgeDocument buildDocument(Long id, Long baseId, Long tenantId, String fileName,
                                              String fileType, long fileSize, String fileUrl, String contentHash,
                                              String docKey, int version) {
        AiKnowledgeDocument doc = new AiKnowledgeDocument();
        doc.setId(id);
        // tenantId 由 MyBatis-Plus 租户插件在 INSERT 时自动注入，不手动 set
        doc.setBaseId(baseId);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc.setFileUrl(fileUrl);
        doc.setContentHash(contentHash);
        doc.setDocKey(docKey);
        doc.setVersion(version);
        // 落库时待生效：唯一索引 uk_02(doc_key) WHERE is_latest=1 只允许一条生效版，
        // 由消费端 KnowledgeVersionCommitService.commit 在单事务里切旧版 0 / 新版 1
        doc.setIsLatest(AiKnowledgeDocument.LATEST_NO);
        doc.setParseStatus(0);
        doc.setParseError("");
        doc.setChunkCount(0);
        doc.setCharCount(0L);
        doc.setEnabled(1);
        doc.setCreator(currentUser());
        doc.setUpdater(currentUser());
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        doc.setDeleted(0);
        return doc;
    }

    private AiKnowledgeDocumentUploadRespVO buildUploadResp(Long documentId, Integer parseStatus, boolean duplicated,
                                                            String docKey, Integer version, Integer isLatest) {
        AiKnowledgeDocumentUploadRespVO resp = new AiKnowledgeDocumentUploadRespVO();
        resp.setDocumentId(documentId);
        resp.setParseStatus(parseStatus);
        resp.setDuplicated(duplicated);
        resp.setDocKey(docKey);
        resp.setVersion(version);
        resp.setIsLatest(isLatest);
        return resp;
    }

    private String currentUser() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "" : userId.toString();
    }
}
