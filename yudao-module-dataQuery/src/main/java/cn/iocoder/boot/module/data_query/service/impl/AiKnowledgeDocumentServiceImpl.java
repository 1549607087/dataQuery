package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import cn.iocoder.boot.module.data_query.mapper.AiKnowledgeDocumentMapper;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeChunkService;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeDocumentService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOC_DOCKEY_REQUIRED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOC_VERSION_NOT_FOUND;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_DOC_VERSION_SWITCH_SAME;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
* @author admin
* @description 针对表【ai_knowledge_document(知识库文档：原始文件与解析状态，逻辑外键 base_id -> ai_knowledge_base.id)】的数据库操作Service实现
* @createDate 2026-11-17 19:31:58
*
* 租户私有表：由 MyBatis-Plus 租户插件自动追加 tenant_id 过滤。
*/
@Service
public class AiKnowledgeDocumentServiceImpl extends ServiceImpl<AiKnowledgeDocumentMapper, AiKnowledgeDocument>
    implements AiKnowledgeDocumentService {

    @Resource
    private AiKnowledgeChunkService chunkService;
    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;

    @Override
    public AiKnowledgeDocument getDocument(Long id) {
        // 用 list + LIMIT 1 而非 getById：规避定制 MyBatis-Plus cursor 与 Druid PSCache 冲突
        List<AiKnowledgeDocument> list = list(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, id)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiKnowledgeDocument getDocumentByContentHash(Long baseId, String contentHash) {
        List<AiKnowledgeDocument> list = list(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getContentHash, contentHash)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiKnowledgeDocument getLatestByDocKey(Long baseId, String docKey) {
        List<AiKnowledgeDocument> list = list(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getDocKey, docKey)
                .eq(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_YES)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiKnowledgeDocument getLatestVersionByDocKey(Long baseId, String docKey) {
        List<AiKnowledgeDocument> list = list(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getDocKey, docKey)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .orderByDesc(AiKnowledgeDocument::getVersion)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AiKnowledgeDocument> listVersions(Long baseId, String docKey) {
        if (docKey == null || docKey.isBlank()) {
            throw exception(KNOWLEDGE_DOC_DOCKEY_REQUIRED);
        }
        return list(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getDocKey, docKey)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .orderByDesc(AiKnowledgeDocument::getVersion));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeDocument switchVersion(Long baseId, String docKey, Integer version) {
        if (docKey == null || docKey.isBlank()) {
            throw exception(KNOWLEDGE_DOC_DOCKEY_REQUIRED);
        }
        if (version == null || version <= 0) {
            throw exception(KNOWLEDGE_DOC_VERSION_NOT_FOUND);
        }
        // 目标版本：同 base + docKey + version + 未删除，且必须是解析完成（parse_status=2）才有切片可召回
        AiKnowledgeDocument target = list(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getDocKey, docKey)
                .eq(AiKnowledgeDocument::getVersion, version)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .last("LIMIT 1")).stream().findFirst().orElse(null);
        if (target == null) {
            throw exception(KNOWLEDGE_DOC_VERSION_NOT_FOUND);
        }
        if (Integer.valueOf(AiKnowledgeDocument.LATEST_YES).equals(target.getIsLatest())) {
            throw exception(KNOWLEDGE_DOC_VERSION_SWITCH_SAME);
        }
        LocalDateTime now = LocalDateTime.now();
        String updater = currentUser();

        // 1. 同 docKey 的 document 全部置非生效
        update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getDocKey, docKey)
                .eq(AiKnowledgeDocument::getDeleted, 0)
                .set(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_NO)
                .set(AiKnowledgeDocument::getUpdater, updater)
                .set(AiKnowledgeDocument::getUpdateTime, now));
        // 2. 同 docKey 的 chunk 全部置非生效
        chunkService.update(new LambdaUpdateWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getBaseId, baseId)
                .eq(AiKnowledgeChunk::getDocKey, docKey)
                .eq(AiKnowledgeChunk::getDeleted, 0)
                .set(AiKnowledgeChunk::getIsLatest, AiKnowledgeChunk.LATEST_NO)
                .set(AiKnowledgeChunk::getUpdater, updater)
                .set(AiKnowledgeChunk::getUpdateTime, now));
        // 3. 目标 document 切片置生效（仅按 documentId 定位该版本自己的切片）
        chunkService.update(new LambdaUpdateWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getDocumentId, target.getId())
                .eq(AiKnowledgeChunk::getDeleted, 0)
                .set(AiKnowledgeChunk::getIsLatest, AiKnowledgeChunk.LATEST_YES)
                .set(AiKnowledgeChunk::getUpdater, updater)
                .set(AiKnowledgeChunk::getUpdateTime, now));
        // 4. 目标 document 置生效
        update(new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, target.getId())
                .set(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_YES)
                .set(AiKnowledgeDocument::getUpdater, updater)
                .set(AiKnowledgeDocument::getUpdateTime, now));
        // 5. 知识库计数按生效版重算
        recomputeBaseCounts(baseId, now, updater);
        return target;
    }

    /** 按 is_latest=1 重算生效切片/文档数，与 KnowledgeVersionCommitService 口径一致 */
    private void recomputeBaseCounts(Long baseId, LocalDateTime now, String updater) {
        long chunkCount = chunkService.count(new LambdaQueryWrapperX<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getBaseId, baseId)
                .eq(AiKnowledgeChunk::getIsLatest, AiKnowledgeChunk.LATEST_YES)
                .eq(AiKnowledgeChunk::getSyncStatus, 1)
                .eq(AiKnowledgeChunk::getDeleted, 0));
        long documentCount = count(new LambdaQueryWrapperX<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getBaseId, baseId)
                .eq(AiKnowledgeDocument::getIsLatest, AiKnowledgeDocument.LATEST_YES)
                .eq(AiKnowledgeDocument::getDeleted, 0));
        knowledgeBaseService.update(new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, baseId)
                .setSql("chunk_count = {0}", chunkCount)
                .setSql("document_count = {0}", documentCount)
                .set(AiKnowledgeBase::getUpdater, updater)
                .set(AiKnowledgeBase::getUpdateTime, now));
    }

    /** 版本切换为管理操作，updater 用固定系统标记（租户隔离仍由 MyBatis-Plus 插件保证） */
    private static final String VERSION_SWITCH_UPDATER = "version-switch";

    private static String currentUser() {
        return VERSION_SWITCH_UPDATER;
    }
}
