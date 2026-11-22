package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiPromptVariable;
import cn.iocoder.boot.module.data_query.framework.tenant.SharedTenantServiceImpl;
import cn.iocoder.boot.module.data_query.service.AiPromptVariableService;
import cn.iocoder.boot.module.data_query.mapper.AiPromptVariableMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_prompt_variable(提示词模板变量（占位符）定义表)】的数据库操作Service实现
* @createDate 2026-11-12 23:22:25
*
* 平台共享表：继承 SharedTenantServiceImpl，由基类统一注入可见/可写的租户范围。
*/
@Service
public class AiPromptVariableServiceImpl extends SharedTenantServiceImpl<AiPromptVariableMapper, AiPromptVariable>
    implements AiPromptVariableService{

    @Override
    public AiPromptVariable getAiPromptVariable(Long id) {
        List<AiPromptVariable> list = list(new LambdaQueryWrapperX<AiPromptVariable>()
                .eq(AiPromptVariable::getId, id)
                .eq(AiPromptVariable::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
