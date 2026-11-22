package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiModelCircuitState;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author admin
* @description 针对表【ai_model_circuit_state(模型熔断状态持久化快照表（运行时以 Redis 为准，本表不承载高频写入）)】的数据库操作Mapper
* @createDate 2026-11-13 20:16:04
* @Entity cn.iocoder.boot.module.data_query.entity.AiModelCircuitState
*/
public interface AiModelCircuitStateMapper extends BaseMapper<AiModelCircuitState> {

}




