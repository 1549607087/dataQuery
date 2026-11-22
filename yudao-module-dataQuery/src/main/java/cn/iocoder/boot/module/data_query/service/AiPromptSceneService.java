package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiPromptScene;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_prompt_scene(提示词场景字典表)】的数据库操作Service
* @createDate 2026-11-12 23:22:25
*/
public interface AiPromptSceneService extends IService<AiPromptScene> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiPromptScene getAiPromptScene(Long id);

    /**
     * 按场景编码查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiPromptScene getAiPromptSceneByCode(String sceneCode);

}
