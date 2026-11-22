package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiPromptVersion;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_prompt_version(提示词版本表（内容快照）)】的数据库操作Service
* @createDate 2026-11-12 23:22:25
*/
public interface AiPromptVersionService extends IService<AiPromptVersion> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiPromptVersion getAiPromptVersion(Long id);

}
