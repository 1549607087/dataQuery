package cn.iocoder.boot.module.data_query.framework.knowledge.version;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeChunkService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeDocumentService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档版本的落库事务：把「旧版失效 + 新版切片插入 + 文档状态回写 + 知识库计数重算」
 * 收进单个事务，保证对外不出现「新版切片已落但状态未回写」或「两版同时生效」的中间态。
 *
 * 设计约束（调用方必须遵守）：
 * 1. 远程 IO（读文件 / 解析 / 切块 / Embedding）不在此事务内，由 KnowledgeIngestService 负责；
 * 2. 新版本 document 在上传落库时 is_latest 必须为 0（待生效）——因为唯一索引
 *    uk_ai_knowledge_document_02(doc_key) WHERE is_latest=1 只允许一个生效版，
 *    上传时若直接写 1 会与旧版冲突；本服务的 commit 是把它切成 1 的唯一入口；
 * 3. chunks 的 version / is_latest / doc_key 由调用方按新版本组装好（is_latest=1）。
 *
 * 同时服务两种场景：
 * - 新版本上传（INCREMENTAL）：docKey 非空，旧版是另一条 document 行，会被置 is_latest=0；
 * - 原版本重建（FULL，rebuild）：docKey 相同且 documentId 相同，ne(documentId) 排除自身，
 *   不会误伤自己；旧切片已在 Controller 的 localTx 里软删。
 */
@Slf4j
@Service
public class KnowledgeVersionCommitService {

    private static final int STATUS_DONE = 2;
    private static final int SYNC_STATUS_BUILT = 1;
    private static final String UPDATER = "mq-consumer";

    @Resource
    private AiKnowledgeDocumentService documentService;
    @Resource
    private AiKnowledgeChunkService chunkService;
    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;

    /**
     * 单事务提交一个新版本的落库结果。
     *
     * @param document  新版本文档（已落库，is_latest=0，parse_status=1）
     * @param chunks    新版切片（已组装好，is_latest=1，version=新版号）
     * @param charCount 解析后全文字符数
     * @param modelCode 本轮 Embedding 模型指纹
     */
    @Transactional(rollbackFor = Exception.class)
    public void commit(AiKnowledgeDocument document, List<AiKnowledgeChunk> chunks,
                       long charCount, String modelCode) {
        Long documentId = document.getId();
        Long baseId = document.getBaseId();
        String docKey = document.getDocKey();
        LocalDateTime now = LocalDateTime.now();

        // 1. 同 doc_key 的旧版本失效（排除自身，兼容 rebuild 原版本重切）
        if (StringUtils.hasText(docKey)) {
            retirePrevious(documentId, baseId, docKey, now);
        }

        // 2. 新版切片整批插入
        chunkService.saveBatch(chunks);

        // 3. 新版文档：切成生效版本 + 回写解析结果
        documentService.update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, documentId)
                .set(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_YES)
                .set(AiKnowledgeDocument::getParseStatus, STATUS_DONE)
                .set(AiKnowledgeDocument::getParseError, "")
                .set(AiKnowledgeDocument::getChunkCount, chunks.size())
                .set(AiKnowledgeDocument::getCharCount, charCount)
                .set(AiKnowledgeDocument::getEnabled, 1)
                .set(AiKnowledgeDocument::getUpdater, UPDATER)
                .set(AiKnowledgeDocument::getUpdateTime, now));

        // 4. 知识库冗余计数按「当前生效版本」重算，避免多版本累加漂移
        recomputeBaseCounts(baseId, modelCode, now);

        log.info("[知识库版本提交] 完成：documentId={}, baseId={}, docKey={}, version={}, chunks={}",
                documentId, baseId, docKey, document.getVersion(), chunks.size());
    }

    /** 旧版本的 document 与 chunk 统一置 is_latest=0（deleted=0 的行；历史软删行不动） */
    private void retirePrevious(Long documentId, Long baseId, String docKey, LocalDateTime now) {
        documentService.update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getDocKey, docKey)
                .eq(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_YES)
                .ne(AiKnowledgeDocument::getId, documentId)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .set(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_NO)
                .set(AiKnowledgeDocument::getUpdater, UPDATER)
                .set(AiKnowledgeDocument::getUpdateTime, now));

        chunkService.update(new LambdaUpdateWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getBaseId, baseId)
                .eq(AiKnowledgeChunk::getDocKey, docKey)
                .eq(AiKnowledgeChunk::getIsLatest, AiKnowledgeChunk.LATEST_YES)
                .ne(AiKnowledgeChunk::getDocumentId, documentId)
                .eq(AiKnowledgeChunk::getDeleted, 0)
                .set(AiKnowledgeChunk::getIsLatest, AiKnowledgeChunk.LATEST_NO)
                .set(AiKnowledgeChunk::getUpdater, UPDATER)
                .set(AiKnowledgeChunk::getUpdateTime, now));
    }

    /** 按 is_latest=1 重算生效切片数 / 生效文档数，覆盖增量与回滚场景 */
    private void recomputeBaseCounts(Long baseId, String modelCode, LocalDateTime now) {
        long chunkCount = chunkService.count(new LambdaQueryWrapperX<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getBaseId, baseId)
                .eq(AiKnowledgeChunk::getIsLatest, AiKnowledgeChunk.LATEST_YES)
                .eq(AiKnowledgeChunk::getSyncStatus, SYNC_STATUS_BUILT)
                .eq(AiKnowledgeChunk::getDeleted, 0));
        long documentCount = documentService.count(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_YES)
                .eq(AiKnowledgeDocument::getDeleted, 0));

        knowledgeBaseService.update(new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, baseId)
                .setSql("chunk_count = {0}", chunkCount)
                .setSql("document_count = {0}", documentCount)
                .set(AiKnowledgeBase::getEmbeddingModelCode, modelCode)
                .set(AiKnowledgeBase::getUpdater, UPDATER)
                .set(AiKnowledgeBase::getUpdateTime, now));

        log.info("[知识库版本提交] 计数重算：baseId={}, chunks={}, documents={}", baseId, chunkCount, documentCount);
    }
}
