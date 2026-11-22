package cn.iocoder.boot.module.data_query.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.iocoder.boot.module.data_query.entity.AiModelRouteCandidate;
import cn.iocoder.boot.module.data_query.service.AiModelRouteCandidateService;
import cn.iocoder.boot.module.data_query.mapper.AiModelRouteCandidateMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author admin
* @description 针对表【ai_model_route_candidate(模型路由候选集（权重 / 优先级 / 成本优先）表)】的数据库操作Service实现
* @createDate 2026-11-13 20:16:04
*/
@Service
public class AiModelRouteCandidateServiceImpl extends ServiceImpl<AiModelRouteCandidateMapper, AiModelRouteCandidate>
    implements AiModelRouteCandidateService{

    @Override
    public AiModelRouteCandidate getAiModelRouteCandidate(Long id) {
        List<AiModelRouteCandidate> list = list(new LambdaQueryWrapperX<AiModelRouteCandidate>()
                .eq(AiModelRouteCandidate::getId, id)
                .eq(AiModelRouteCandidate::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

}
