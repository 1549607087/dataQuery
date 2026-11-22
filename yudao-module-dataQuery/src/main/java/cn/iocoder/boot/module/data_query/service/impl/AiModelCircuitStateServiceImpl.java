package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelCircuitState;
import cn.iocoder.boot.module.data_query.service.AiModelCircuitStateService;
import cn.iocoder.boot.module.data_query.mapper.AiModelCircuitStateMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_circuit_state(模型熔断状态持久化快照表（运行时以 Redis 为准，本表不承载高频写入）)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelCircuitStateServiceImpl extends ServiceImpl<AiModelCircuitStateMapper, AiModelCircuitState>
    implements AiModelCircuitStateService{

    @Override
    public AiModelCircuitState getAiModelCircuitState(Long id) {
        List<AiModelCircuitState> list = list(new LambdaQueryWrapperX<AiModelCircuitState>()
                .eq(AiModelCircuitState::getId, id)
                .eq(AiModelCircuitState::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiModelCircuitState getAiModelCircuitStateByKey(String circuitKey) {
        List<AiModelCircuitState> list = list(new LambdaQueryWrapperX<AiModelCircuitState>()
                .eq(AiModelCircuitState::getCircuitKey, circuitKey)
                .eq(AiModelCircuitState::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
