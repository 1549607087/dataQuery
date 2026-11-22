package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeBase;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeBaseService;
import cn.iocoder.boot.module.data_query.mapper.AiKnowledgeBaseMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_knowledge_base(知识库：租户私有的文档集合，独立问答旁路的顶层实体)】的数据库操作Service实现
* @createDate 2026-11-17 19:31:58
*
* 租户私有表：不涉及 SharedTenantServiceImpl，由 MyBatis-Plus 租户插件自动追加 tenant_id 过滤。
*/
@Service
public class AiKnowledgeBaseServiceImpl extends ServiceImpl<AiKnowledgeBaseMapper, AiKnowledgeBase>
    implements AiKnowledgeBaseService{

    @Override
    public AiKnowledgeBase getKnowledgeBase(Long id) {
        List<AiKnowledgeBase> list = list(new LambdaQueryWrapperX<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, id)
                .eq(AiKnowledgeBase::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiKnowledgeBase getKnowledgeBaseByCode(String code) {
        List<AiKnowledgeBase> list = list(new LambdaQueryWrapperX<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getCode, code)
                .eq(AiKnowledgeBase::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
