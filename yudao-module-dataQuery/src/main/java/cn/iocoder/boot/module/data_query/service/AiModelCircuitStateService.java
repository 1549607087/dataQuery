package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiModelCircuitState;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_model_circuit_state(模型熔断状态持久化快照表（运行时以 Redis 为准，本表不承载高频写入）)】的数据库操作Service
* @createDate 2026-11-13 20:16:04
*/
public interface AiModelCircuitStateService extends IService<AiModelCircuitState> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiModelCircuitState getAiModelCircuitState(Long id);

    /**
     * 按熔断维度键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiModelCircuitState getAiModelCircuitStateByKey(String circuitKey);

}
