package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 问数会话 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId。按主键查询统一走 selectList + LIMIT 1，
 * 避开 MP 3.5.17 cursor 与 Druid PSCache 冲突导致的 500。
 */
public interface AiQuerySessionService extends IService<AiQuerySession> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiQuerySession getAiQuerySession(Long id);

    Long createAiQuerySession(AiQuerySession entity);

    void updateAiQuerySession(AiQuerySession entity);

    void deleteAiQuerySession(Long id);

    void deleteAiQuerySessionList(List<Long> ids);

    /**
     * 推进当前激活分支末端指针（对话树模型）。
     * 只更新 current_leaf_id 与 last_ask_time，不累加 message_count
     * ——重新生成 / 修改问题 / 切换版本都会推进叶子，但不应被当成「新一轮提问」。
     */
    void advanceLeaf(Long sessionId, Long leafId);
}
