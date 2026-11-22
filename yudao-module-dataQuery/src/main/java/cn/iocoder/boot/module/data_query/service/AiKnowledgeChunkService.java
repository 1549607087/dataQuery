package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_knowledge_chunk(知识库切片：带向量与引用定位信息，pgvector 检索的最小单位，租户私有)】的数据库操作Service
* @createDate 2026-11-17 19:31:58
*/
public interface AiKnowledgeChunkService extends IService<AiKnowledgeChunk> {

    /**
     * 增量复用的参照：取上一版 document 的有效切片（未删除 + 已构建）。
     * 租户隔离由 MyBatis-Plus 插件自动追加 tenant_id；调用方须保证 documentId 属于当前租户。
     */
    List<AiKnowledgeChunk> listActiveByDocumentId(Long documentId);
}
