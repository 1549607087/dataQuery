package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import cn.iocoder.boot.module.data_query.service.AiModelSceneBindingService;
import cn.iocoder.boot.module.data_query.mapper.AiModelSceneBindingMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_scene_binding(模型场景绑定（路由与降级）表)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelSceneBindingServiceImpl extends ServiceImpl<AiModelSceneBindingMapper, AiModelSceneBinding>
    implements AiModelSceneBindingService{

    @Override
    public AiModelSceneBinding getAiModelSceneBinding(Long id) {
        List<AiModelSceneBinding> list = list(new LambdaQueryWrapperX<AiModelSceneBinding>()
                .eq(AiModelSceneBinding::getId, id)
                .eq(AiModelSceneBinding::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
