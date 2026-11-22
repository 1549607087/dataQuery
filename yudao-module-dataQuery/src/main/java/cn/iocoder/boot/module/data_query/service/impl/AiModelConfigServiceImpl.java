package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.service.AiModelConfigService;
import cn.iocoder.boot.module.data_query.mapper.AiModelConfigMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_config(模型配置表)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelConfigServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig>
    implements AiModelConfigService{

    @Override
    public AiModelConfig getAiModelConfig(Long id) {
        // 使用 selectList 而非 IRepository#getOne：后者在 MP 3.5.17 走 selectCursor，
        // 与 Druid PSCache 组合时可能复用已关闭的 Statement
        List<AiModelConfig> list = list(new LambdaQueryWrapperX<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .eq(AiModelConfig::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}




