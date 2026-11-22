package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiModelRouteCandidate;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author admin
* @description 针对表【ai_model_route_candidate(模型路由候选集（权重 / 优先级 / 成本优先）表)】的数据库操作Service
* @createDate 2026-11-13 20:16:04
*/
public interface AiModelRouteCandidateService extends IService<AiModelRouteCandidate> {

    /**
     * 按主键查询未删除记录（走 selectList，避开 MyBatis-Plus cursor 路径与 Druid PSCache 冲突）
     */
    AiModelRouteCandidate getAiModelRouteCandidate(Long id);

}
