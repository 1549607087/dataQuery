package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiPrompt;
import cn.iocoder.boot.module.data_query.framework.tenant.SharedTenantServiceImpl;
import cn.iocoder.boot.module.data_query.service.AiPromptService;
import cn.iocoder.boot.module.data_query.mapper.AiPromptMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_prompt(提示词模板主表（当前生效内容）)】的数据库操作Service实现
* @createDate 2026-11-12 23:22:25
*
* 平台共享表：继承 SharedTenantServiceImpl，由基类统一注入可见/可写的租户范围。
*/
@Service
public class AiPromptServiceImpl extends SharedTenantServiceImpl<AiPromptMapper, AiPrompt>
    implements AiPromptService{

    @Override
    public AiPrompt getAiPrompt(Long id) {
        List<AiPrompt> list = list(new LambdaQueryWrapperX<AiPrompt>()
                .eq(AiPrompt::getId, id)
                .eq(AiPrompt::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
