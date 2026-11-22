package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_knowledge_document(知识库文档：原始文件与解析状态，逻辑外键 base_id -> ai_knowledge_base.id)】的数据库操作Service
* @createDate 2026-11-17 19:31:58
*/
public interface AiKnowledgeDocumentService extends IService<AiKnowledgeDocument> {

    /**
     * 按主键查询（未删除）。租户隔离由 MyBatis-Plus 插件自动追加 tenant_id。
     */
    AiKnowledgeDocument getDocument(Long id);

    /**
     * 上传幂等：按 (baseId, contentHash) 查未删除的文档。
     * 命中唯一键 uk_ai_knowledge_document_01(tenant_id, base_id, content_hash)。
     */
    AiKnowledgeDocument getDocumentByContentHash(Long baseId, String contentHash);

    /**
     * 版本链入口：按 (baseId, docKey) 查当前生效版本（is_latest=1 AND deleted=0）。
     * 上传方据此判断「幂等复用 / 内容变化升新版本」。
     * 同 doc_key 至多一条生效版（唯一索引 uk_ai_knowledge_document_02 保证），故无需 LIMIT。
     */
    AiKnowledgeDocument getLatestByDocKey(Long baseId, String docKey);

    /**
     * 版本链：按 (baseId, docKey) 查现存（deleted=0）版本号最高的一条，不限 is_latest。
     *
     * <p>供上传分配新版本号用：唯一键 uk_ai_knowledge_document_01 约束的是
     * (tenant_id, base_id, doc_key, version) WHERE deleted=0，因此新版本号必须在
     * 所有未删除版本之上递增，而不能只看当前生效版——否则删掉生效版后版本号会
     * 回退到已被历史版本占用的值，触发重复键冲突。
     *
     * <p>注意本方法可能返回 is_latest=0 的历史版本（生效版被删、或手动回滚过），
     * 仅用于版本号分配与增量复用参照，不代表当前生效版。
     */
    AiKnowledgeDocument getLatestVersionByDocKey(Long baseId, String docKey);

    /**
     * 列出某逻辑文档的全部版本（含生效标记 / 解析状态 / 切片数），按 version 降序。
     */
    List<AiKnowledgeDocument> listVersions(Long baseId, String docKey);

    /**
     * 手动切换当前生效版本：同 doc_key 全部置非生效，目标版本置生效（document + chunk 两表同事务）。
     *
     * @return 切换后的目标版本文档
     */
    AiKnowledgeDocument switchVersion(Long baseId, String docKey, Integer version);
}
