package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiPromptVariable;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_prompt_variable(提示词模板变量（占位符）定义表)】的数据库操作Service
* @createDate 2026-11-12 23:22:25
*/
public interface AiPromptVariableService extends IService<AiPromptVariable> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiPromptVariable getAiPromptVariable(Long id);

}
