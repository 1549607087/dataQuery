package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelCallLog;
import cn.iocoder.boot.module.data_query.service.AiModelCallLogService;
import cn.iocoder.boot.module.data_query.mapper.AiModelCallLogMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_call_log(模型调用日志（用量与成本）表)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelCallLogServiceImpl extends ServiceImpl<AiModelCallLogMapper, AiModelCallLog>
    implements AiModelCallLogService{

    @Override
    public AiModelCallLog getAiModelCallLog(Long id) {
        List<AiModelCallLog> list = list(new LambdaQueryWrapperX<AiModelCallLog>()
                .eq(AiModelCallLog::getId, id)
                .eq(AiModelCallLog::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
