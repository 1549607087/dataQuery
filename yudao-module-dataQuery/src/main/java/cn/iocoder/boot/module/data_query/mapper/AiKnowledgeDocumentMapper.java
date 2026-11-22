package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author admin
* @description 针对表【ai_knowledge_document(知识库文档：原始文件与解析状态，逻辑外键 base_id -> ai_knowledge_base.id)】的数据库操作Mapper
* @createDate 2026-11-17 19:31:58
* @Entity cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument
*/
public interface AiKnowledgeDocumentMapper extends BaseMapper<AiKnowledgeDocument> {

}




