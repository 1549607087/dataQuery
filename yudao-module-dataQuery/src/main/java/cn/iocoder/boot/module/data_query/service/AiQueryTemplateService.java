package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.entity.AiQueryTemplate;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 查询模板 Service
 *
 * 全部方法都在「当前租户」范围内：查询依赖 TenantDatabaseInterceptor 追加 tenant_id，
 * 写入显式 setTenantId。按主键查询统一走 selectList + LIMIT 1，
 * 避开 MP 3.5.17 cursor 与 Druid PSCache 冲突导致的 500。
 */
public interface AiQueryTemplateService extends IService<AiQueryTemplate> {

    /** 按主键查询未删除记录（selectList + LIMIT 1，避开 cursor 路径） */
    AiQueryTemplate getAiQueryTemplate(Long id);

    Long createAiQueryTemplate(AiQueryTemplate entity);

    void updateAiQueryTemplate(AiQueryTemplate entity);

    void deleteAiQueryTemplate(Long id);

    void deleteAiQueryTemplateList(List<Long> ids);
}
