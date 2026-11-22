package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiPromptVersion;
import cn.iocoder.boot.module.data_query.framework.tenant.SharedTenantServiceImpl;
import cn.iocoder.boot.module.data_query.service.AiPromptVersionService;
import cn.iocoder.boot.module.data_query.mapper.AiPromptVersionMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_prompt_version(提示词版本表（内容快照）)】的数据库操作Service实现
* @createDate 2026-11-12 23:22:25
*
* 平台共享表：继承 SharedTenantServiceImpl，由基类统一注入可见/可写的租户范围。
*/
@Service
public class AiPromptVersionServiceImpl extends SharedTenantServiceImpl<AiPromptVersionMapper, AiPromptVersion>
    implements AiPromptVersionService{

    @Override
    public AiPromptVersion getAiPromptVersion(Long id) {
        List<AiPromptVersion> list = list(new LambdaQueryWrapperX<AiPromptVersion>()
                .eq(AiPromptVersion::getId, id)
                .eq(AiPromptVersion::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
