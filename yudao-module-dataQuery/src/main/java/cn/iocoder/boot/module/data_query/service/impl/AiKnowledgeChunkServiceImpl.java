package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeChunkService;
import cn.iocoder.boot.module.data_query.mapper.AiKnowledgeChunkMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_knowledge_chunk(知识库切片：带向量与引用定位信息，pgvector 检索的最小单位，租户私有)】的数据库操作Service实现
* @createDate 2026-11-17 19:31:58
*/
@Service
public class AiKnowledgeChunkServiceImpl extends ServiceImpl<AiKnowledgeChunkMapper, AiKnowledgeChunk>
    implements AiKnowledgeChunkService{

    @Override
    public List<AiKnowledgeChunk> listActiveByDocumentId(Long documentId) {
        return list(new LambdaQueryWrapperX<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getDocumentId, documentId)
                .eq(AiKnowledgeChunk::getDeleted, 0)
                .eq(AiKnowledgeChunk::getSyncStatus, 1)); // 只取已构建的切片参与复用
    }
}




