package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.entity.AiPromptScene;
import cn.iocoder.boot.module.data_query.framework.tenant.SharedTenantServiceImpl;
import cn.iocoder.boot.module.data_query.service.AiPromptSceneService;
import cn.iocoder.boot.module.data_query.mapper.AiPromptSceneMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_prompt_scene(提示词场景字典表)】的数据库操作Service实现
* @createDate 2026-11-12 23:22:25
*
* 平台共享表：继承 SharedTenantServiceImpl，由基类统一注入可见/可写的租户范围。
*/
@Service
public class AiPromptSceneServiceImpl extends SharedTenantServiceImpl<AiPromptSceneMapper, AiPromptScene>
    implements AiPromptSceneService{

    @Override
    public AiPromptScene getAiPromptScene(Long id) {
        List<AiPromptScene> list = list(new LambdaQueryWrapperX<AiPromptScene>()
                .eq(AiPromptScene::getId, id)
                .eq(AiPromptScene::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiPromptScene getAiPromptSceneByCode(String sceneCode) {
        // 租户覆盖优先：同一 scene_code 同时存在平台(0)与租户记录时取 tenant_id 大者
        List<AiPromptScene> list = list(new LambdaQueryWrapperX<AiPromptScene>()
                .eq(AiPromptScene::getSceneCode, sceneCode)
                .eq(AiPromptScene::getDeleted, 0)
                .orderByDesc(AiPromptScene::getTenantId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
