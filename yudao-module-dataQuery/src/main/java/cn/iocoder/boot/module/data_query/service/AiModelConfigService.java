package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_model_config(模型配置表)】的数据库操作Service
* @createDate 2026-11-13 20:16:04
*/
public interface AiModelConfigService extends IService<AiModelConfig> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiModelConfig getAiModelConfig(Long id);

}
