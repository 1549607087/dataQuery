package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiPrompt;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_prompt(提示词模板主表（当前生效内容）)】的数据库操作Service
* @createDate 2026-11-12 23:22:25
*/
public interface AiPromptService extends IService<AiPrompt> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiPrompt getAiPrompt(Long id);

}
