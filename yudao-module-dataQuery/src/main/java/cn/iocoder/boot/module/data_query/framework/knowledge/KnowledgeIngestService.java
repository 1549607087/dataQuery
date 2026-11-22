package cn.iocoder.boot.module.data_query.framework.knowledge;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import cn.iocoder.boot.module.data_query.framework.knowledge.chunk.ChunkDraft;
import cn.iocoder.boot.module.data_query.framework.knowledge.chunk.ChunkSplitter;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.DocumentParser;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.DocumentParserFactory;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.DocumentTextRewriter;
import cn.iocoder.boot.module.data_query.framework.knowledge.parser.ParsedDocument;
import cn.iocoder.boot.module.data_query.framework.knowledge.table.TableNarrateService;
import cn.iocoder.boot.module.data_query.framework.knowledge.version.KnowledgeVersionCommitService;
import cn.iocoder.boot.module.data_query.framework.knowledge.version.KnowledgeVersionUtil;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaEmbeddingProvider;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeChunkService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeDocumentService;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档摄取服务：把「已落库的 document」解析、切块、向量化后写入 ai_knowledge_chunk。
 *
 * 由 MQ 消费端调用，也可在 mq.enabled=false 时同步调用（本地无 MQ 排障）。
 * 全流程租户隔离依赖调用方已设置 TenantContextHolder（消费端 set/clear，同步链路本就有上下文）。
 *
 * 关键设计：
 * 1. 三方校验：消息属性 tenantId / body tenantId / 库记录 tenantId 必须一致，防串租户；
 * 2. 幂等：parse_status 只能 0(待解析) 或 3(失败) 才能进入，用 CAS 抢占为 1(解析中)，
 *    重复投递 / 并发消费都只会有一个真正执行；
 * 3. 状态机：成功=2(已完成)，失败=3(失败+parse_error)，绝不停在 1；
 * 4. 向量缺失降级：租户未配 Embedding 时，chunk 仍落库但 sync_status=0(待构建)，
 *    document 记 parse_status=2，不伪造向量；下次 rebuild 可补向量；
 * 5. content_hash = SHA256(content + "|" + embeddingModelCode)，换模型自动触发重建；
 * 6. 增量复用（P9.3c）：新版本上传时按上一版 content_hash 复用未变切片的向量，0 次 Embedding；
 *    落库阶段（切 latest + 插 chunk + 回写 + 计数）由 KnowledgeVersionCommitService 单事务完成。
 */
@Slf4j
@Service
public class KnowledgeIngestService {

    /** parse_status：0 待解析 / 1 解析中 / 2 已完成 / 3 失败 / 4 已下线 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PARSING = 1;
    private static final int STATUS_FAILED = 3;

    @Resource
    private KnowledgeProperties properties;
    @Resource
    private AiKnowledgeDocumentService documentService;
    @Resource
    private AiKnowledgeChunkService chunkService;
    @Resource
    private DocumentParserFactory parserFactory;
    @Resource
    private ChunkSplitter chunkSplitter;
    @Resource
    private TableNarrateService tableNarrateService;
    @Resource
    private DocumentTextRewriter documentTextRewriter;
    @Resource
    private SchemaEmbeddingProvider embeddingProvider;
    @Resource
    private FileService fileService;
    @Resource
    private KnowledgeVersionCommitService versionCommitService;

    /**
     * 摄取入口。
     *
     * @param message          消息体
     * @param attrTenantId     消息属性里的 tenantId（与 body 做三方校验）
     * @return true 本次真正执行了摄取；false 被幂等跳过 / 校验拦截
     */
    public boolean ingest(KnowledgeParseMessage message, Long attrTenantId) {
        Long documentId = message.getDocumentId();
        if (documentId == null) {
            log.warn("[知识库摄取] 消息缺少 documentId，跳过：{}", message);
            return false;
        }
        AiKnowledgeDocument document = documentService.getDocument(documentId);
        if (document == null) {
            log.warn("[知识库摄取] document 不存在或已删除，跳过：documentId={}", documentId);
            return false;
        }

        // 1. 三方校验：属性 / body / 库记录 tenantId + baseId 必须一致
        if (!verifyTenantAndBase(message, attrTenantId, document)) {
            markFailed(documentId, "租户或知识库归属校验失败，已拒绝摄取");
            return false;
        }

        // 2. 幂等 CAS：仅 0(待解析)/3(失败) 可抢占为 1(解析中)
        if (!claimParsing(document)) {
            Integer status = document.getParseStatus();
            log.info("[知识库摄取] 幂等跳过：documentId={}, parseStatus={}", documentId, status);
            return false;
        }

        try {
            doIngest(message, document);
            return true;
        } catch (Exception ex) {
            log.error("[知识库摄取] 失败：documentId={}, fileUrl={}", documentId, document.getFileUrl(), ex);
            markFailed(documentId, ex.getMessage());
            return false;
        }
    }

    /** 三方校验：消息属性 tenantId == body tenantId == 库 tenantId；baseId 也要一致 */
    private boolean verifyTenantAndBase(KnowledgeParseMessage message, Long attrTenantId,
                                        AiKnowledgeDocument document) {
        Long bodyTenantId = message.getTenantId();
        Long dbTenantId = document.getTenantId();
        boolean tenantOk = attrTenantId != null && bodyTenantId != null && dbTenantId != null
                && attrTenantId.equals(bodyTenantId) && bodyTenantId.equals(dbTenantId);
        boolean baseOk = message.getBaseId() != null && message.getBaseId().equals(document.getBaseId());
        if (!tenantOk || !baseOk) {
            log.error("[知识库摄取] 三方校验失败：attrTenant={}, bodyTenant={}, dbTenant={}, "
                            + "bodyBase={}, dbBase={}, documentId={}",
                    attrTenantId, bodyTenantId, dbTenantId, message.getBaseId(), document.getBaseId(),
                    message.getDocumentId());
            return false;
        }
        return true;
    }

    /** CAS 抢占解析权：update ... where parse_status in (0,3)，返回是否抢到 */
    private boolean claimParsing(AiKnowledgeDocument document) {
        return documentService.update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, document.getId())
                .in(AiKnowledgeDocument::getParseStatus, STATUS_PENDING, STATUS_FAILED)
                .set(AiKnowledgeDocument::getParseStatus, STATUS_PARSING)
                .set(AiKnowledgeDocument::getParseError, "")
                .set(AiKnowledgeDocument::getUpdater, "mq-consumer")
                .set(AiKnowledgeDocument::getUpdateTime, LocalDateTime.now()));
    }

    /** 实际摄取流程：读文件 -> 解析 -> 切块 -> 增量复用/向量化 -> 单事务落库 */
    private void doIngest(KnowledgeParseMessage message, AiKnowledgeDocument document) {
        Long tenantId = document.getTenantId();
        Long documentId = document.getId();

        // 1. 按 fileUrl 读文件字节
        byte[] bytes;
        try {
            bytes = fileService.getFileContentByUrl(document.getFileUrl());
        } catch (Exception ex) {
            throw new IllegalStateException("读取文件失败: " + document.getFileUrl(), ex);
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("文件内容为空: " + document.getFileUrl());
        }

        // 2. 解析（不支持的类型明确失败，绝不静默成功）
        DocumentParser parser = parserFactory.getParser(document.getFileType());
        if (parser == null) {
            throw new IllegalStateException("暂不支持解析的文件类型: " + document.getFileType());
        }
        ParsedDocument parsed;
        try {
            parsed = parser.parse(bytes, document.getFileName());
        } catch (Exception ex) {
            throw new IllegalStateException("文档解析失败: " + document.getFileName(), ex);
        }

        // 2.5 表格 LLM 叙述化：把 TABLE 块展开成 TABLE_NARRATION 块并重建下标。
        // 叙述服务内部对每个表独立降级；这里再包一层 try-catch，确保任何异常都不影响后续切块落库。
        try {
            String traceId = "ingest-" + documentId;
            Map<Integer, List<String>> narrated = tableNarrateService.narrate(parsed, tenantId, traceId);
            if (narrated != null && !narrated.isEmpty()) {
                parsed = documentTextRewriter.rebuild(parsed, narrated);
            }
        } catch (Exception ex) {
            log.warn("[知识库摄取] 表格叙述阶段异常，改用原表文本继续：documentId={}, reason={}",
                    documentId, ex.getMessage());
        }

        // 3. 切块（重叠默认按 chunk-size 的 20% 计算，兼容旧版绝对配置）
        int chunkSize = properties.getChunkSize() != null ? properties.getChunkSize() : 500;
        int overlap = resolveChunkOverlap(properties, chunkSize);
        List<ChunkDraft> drafts = chunkSplitter.split(parsed, chunkSize, overlap);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("文档未切出任何有效切片: " + document.getFileName());
        }

        // 4. 模型指纹：hash 含 modelCode，换模型自动触发重建，复用时也只复用同模型向量
        String modelCode = embeddingProvider.currentModelCode(tenantId);

        // 5. 增量复用：按上一版 content_hash 命中则复用向量（0 次 Embedding），未命中才embed
        //    参照版本取自消息 previousDocumentId（首版 / rebuild 为 null → 全量 embed）
        long fullTextLength = parsed.getFullText().length();
        List<String> vectors = resolveVectors(tenantId, documentId, message.getPreviousDocumentId(),
                drafts, modelCode);
        int reused = countNonNull(vectors);
        int needEmbed = drafts.size() - reused;

        // 6. 组装新版 chunk（带版本元信息，is_latest=1 待 commit 切成生效）
        List<AiKnowledgeChunk> chunks = buildChunks(document, drafts, modelCode, vectors);

        // 7. 单事务落库：旧版 is_latest 置 0 + 新版 chunk 插入 + 文档回写 + 基数重算
        versionCommitService.commit(document, chunks, fullTextLength, modelCode);

        log.info("[知识库摄取] 完成：documentId={}, baseId={}, tenantId={}, chunks={}, reused={}, embedded={}, chars={}, model={}",
                documentId, document.getBaseId(), tenantId, chunks.size(), reused, needEmbed, fullTextLength, modelCode);
    }

    /**
     * 增量向量复用：返回与 drafts 等长的向量列表（命中复用的位置为上一版向量字面量，未命中为 null）。
     *
     * <p>上一版切片按 content_hash 建映射（只收 sync_status=1 且 embedding 非空的行），
     * hash = SHA256(content + "|" + modelCode) 与 buildChunks / 存量公式完全一致，
     * 因此命中即同内容同模型，可直接复用向量，避免重复 Embedding 成本。
     *
     * <p>租户未配 Embedding 时：能复用的照旧复用，未命中的保持 null（sync_status=0 待构建），不伪造向量。
     */
    private List<String> resolveVectors(Long tenantId, Long documentId, Long previousDocumentId,
                                        List<ChunkDraft> drafts, String modelCode) {
        int size = drafts.size();
        List<String> vectors = new ArrayList<>(Collections.nCopies(size, null));
        if (previousDocumentId == null) {
            // 首版 / rebuild：无参照，全量走现有 embed 流程
            if (embeddingProvider.isAvailable(tenantId)) {
                List<String> all = embedInBatches(tenantId, drafts);
                return all;
            }
            log.warn("[知识库摄取] 租户未配置 Embedding，chunk 落库为待构建：tenantId={}, documentId={}",
                    tenantId, documentId);
            return vectors;
        }

        // 上一版 hash -> embedding 映射
        Map<String, String> oldByHash = new HashMap<>();
        for (AiKnowledgeChunk old : chunkService.listActiveByDocumentId(previousDocumentId)) {
            if (StringUtils.hasText(old.getEmbedding()) && StringUtils.hasText(old.getContentHash())) {
                oldByHash.putIfAbsent(old.getContentHash(), old.getEmbedding());
            }
        }

        // 命中复用；未命中记录稀疏下标
        List<Integer> needWrite = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String hash = KnowledgeVersionUtil.computeContentHash(drafts.get(i).getContent(), modelCode);
            String reused = oldByHash.get(hash);
            if (reused != null) {
                vectors.set(i, reused);
            } else {
                needWrite.add(i);
            }
        }

        // 只对未命中项批量 embed（仿 SchemaVectorBuildServiceImpl 的稀疏下标写法）
        if (!needWrite.isEmpty() && embeddingProvider.isAvailable(tenantId)) {
            List<String> texts = new ArrayList<>(needWrite.size());
            for (int idx : needWrite) {
                texts.add(drafts.get(idx).getContent());
            }
            List<String> embedded = embedInBatchesByText(tenantId, texts);
            for (int j = 0; j < needWrite.size(); j++) {
                vectors.set(needWrite.get(j), embedded.get(j));
            }
        } else if (!needWrite.isEmpty()) {
            log.warn("[知识库摄取] 租户未配置 Embedding，未命中切片落库为待构建：tenantId={}, documentId={}, pending={}",
                    tenantId, documentId, needWrite.size());
        }
        return vectors;
    }

    private static int countNonNull(List<String> list) {
        int n = 0;
        for (String v : list) {
            if (v != null) {
                n++;
            }
        }
        return n;
    }

    /** 分批向量化，返回与 drafts 顺序一一对应的 pgvector 字面量 */
    private List<String> embedInBatches(Long tenantId, List<ChunkDraft> drafts) {
        return embedInBatchesByText(tenantId, drafts.stream().map(ChunkDraft::getContent).toList());
    }

    /** 按文本列表分批向量化，返回与输入等长的 pgvector 字面量；供全量与稀疏增量两路共用 */
    private List<String> embedInBatchesByText(Long tenantId, List<String> texts) {
        int batchSize = properties.getEmbeddingBatchSize() != null ? properties.getEmbeddingBatchSize() : 32;
        if (batchSize <= 0) {
            batchSize = 32;
        }
        List<String> all = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            List<String> batchVectors = embeddingProvider.embed(tenantId, texts.subList(from, to));
            if (batchVectors == null || batchVectors.size() != to - from) {
                throw new IllegalStateException("Embedding 返回数量与输入不一致: expected=" + (to - from)
                        + " actual=" + (batchVectors == null ? 0 : batchVectors.size()));
            }
            all.addAll(batchVectors);
        }
        return all;
    }

    /** 组装 chunk：溯源信息来自 ChunkDraft；content_hash 含模型指纹；向量逐切片判断（增量下部分复用部分待构建） */
    private List<AiKnowledgeChunk> buildChunks(AiKnowledgeDocument document, List<ChunkDraft> drafts,
                                               String modelCode, List<String> vectors) {
        boolean hasAnyVector = vectors != null && vectors.stream().anyMatch(java.util.Objects::nonNull);
        int dim = hasAnyVector ? embeddingProvider.dimension() : 0;
        LocalDateTime now = LocalDateTime.now();
        List<AiKnowledgeChunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            String vector = vectors == null ? null : vectors.get(i);
            boolean hasVector = vector != null;
            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setId(IdWorker.getId());
            chunk.setTenantId(document.getTenantId());
            chunk.setBaseId(document.getBaseId());
            chunk.setDocumentId(document.getId());
            // 版本元信息冗余到 chunk，检索免 join document；新版先 is_latest=1，commit 单事务切生效
            chunk.setDocKey(document.getDocKey());
            chunk.setVersion(document.getVersion() == null ? AiKnowledgeDocument.VERSION_FIRST : document.getVersion());
            chunk.setIsLatest(AiKnowledgeChunk.LATEST_YES);
            chunk.setFileName(document.getFileName());
            chunk.setChunkIndex(draft.getChunkIndex());
            chunk.setContent(draft.getContent());
            chunk.setContentHash(KnowledgeVersionUtil.computeContentHash(draft.getContent(), modelCode));
            chunk.setTokenCount(draft.getTokenCount());
            chunk.setEmbedding(hasVector ? vector : null);
            chunk.setEmbeddingModelCode(modelCode);
            chunk.setEmbeddingDim(hasVector ? dim : 0);
            chunk.setSyncStatus(hasVector ? 1 : 0); // 1 已构建 / 0 待构建
            chunk.setPageNo(draft.getPageNo());
            chunk.setHeadingPath(draft.getHeadingPath());
            chunk.setCharStart(draft.getCharStart());
            chunk.setCharEnd(draft.getCharEnd());
            chunk.setStatus(0);
            chunk.setCreator("mq-consumer");
            chunk.setUpdater("mq-consumer");
            chunk.setCreateTime(now);
            chunk.setUpdateTime(now);
            chunk.setDeleted(0);
            chunks.add(chunk);
        }
        return chunks;
    }

    private void markFailed(Long documentId, String reason) {
        String safe = reason == null ? "未知错误" : reason.substring(0, Math.min(500, reason.length()));
        documentService.update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, documentId)
                .set(AiKnowledgeDocument::getParseStatus, STATUS_FAILED)
                .set(AiKnowledgeDocument::getParseError, safe)
                .set(AiKnowledgeDocument::getUpdater, "mq-consumer")
                .set(AiKnowledgeDocument::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 计算切块重叠 token 数：chunkSize × ratio（默认 20%）。
     * 比例夹在 [0, 0.9]，保证 overlap < chunkSize，避免 ChunkSplitter 里 step <= 0 的死循环兜底被触发。
     */
    static int resolveChunkOverlap(KnowledgeProperties properties, int chunkSize) {
        Double ratio = properties.getChunkOverlapRatio();
        double r = ratio != null ? ratio : 0.2;
        r = Math.max(0.0, Math.min(r, 0.9));
        return (int) Math.round(chunkSize * r);
    }

}
