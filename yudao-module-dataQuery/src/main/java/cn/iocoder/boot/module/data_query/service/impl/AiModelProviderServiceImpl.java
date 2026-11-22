package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelProvider;
import cn.iocoder.boot.module.data_query.service.AiModelProviderService;
import cn.iocoder.boot.module.data_query.mapper.AiModelProviderMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_provider(模型供应商（接入凭证）表)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelProviderServiceImpl extends ServiceImpl<AiModelProviderMapper, AiModelProvider>
    implements AiModelProviderService{

    @Override
    public AiModelProvider getAiModelProvider(Long id) {
        List<AiModelProvider> list = list(new LambdaQueryWrapperX<AiModelProvider>()
                .eq(AiModelProvider::getId, id)
                .eq(AiModelProvider::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
