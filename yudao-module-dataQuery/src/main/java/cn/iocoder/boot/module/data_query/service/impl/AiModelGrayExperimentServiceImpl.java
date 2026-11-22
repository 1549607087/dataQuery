package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelGrayExperiment;
import cn.iocoder.boot.module.data_query.service.AiModelGrayExperimentService;
import cn.iocoder.boot.module.data_query.mapper.AiModelGrayExperimentMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_gray_experiment(模型 / 提示词灰度实验表)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelGrayExperimentServiceImpl extends ServiceImpl<AiModelGrayExperimentMapper, AiModelGrayExperiment>
    implements AiModelGrayExperimentService{

    @Override
    public AiModelGrayExperiment getAiModelGrayExperiment(Long id) {
        List<AiModelGrayExperiment> list = list(new LambdaQueryWrapperX<AiModelGrayExperiment>()
                .eq(AiModelGrayExperiment::getId, id)
                .eq(AiModelGrayExperiment::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
