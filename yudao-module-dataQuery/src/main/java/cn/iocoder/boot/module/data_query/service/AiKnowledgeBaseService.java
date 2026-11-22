package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_knowledge_base(知识库：租户私有的文档集合，独立问答旁路的顶层实体)】的数据库操作Service
* @createDate 2026-11-17 19:31:58
*/
public interface AiKnowledgeBaseService extends IService<AiKnowledgeBase> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiKnowledgeBase getKnowledgeBase(Long id);

    /**
     * 按业务编码查询未删除记录（租户内唯一，走 selectList + LIMIT 1）
     */
    AiKnowledgeBase getKnowledgeBaseByCode(String code);

}
