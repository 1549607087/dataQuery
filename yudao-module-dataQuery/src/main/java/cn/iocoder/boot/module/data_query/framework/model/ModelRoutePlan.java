package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.entity.AiModelConfig;
import cn.iocoder.boot.module.data_query.entity.AiModelSceneBinding;
import lombok.Data;

import java.util.List;

/**
 * 路由决策结果
 *
 * 每次调用都重新查库生成，刻意不加本地缓存：启停、灰度比例、候选权重的修改必须即时生效，
 * 否则「停用」会变成摆设。后续若加缓存，TTL 应 <= 10s 且配合配置变更广播失效。
 */
@Data
public class ModelRoutePlan {

    /** 命中的场景绑定 */
    private AiModelSceneBinding binding;

    /** 已按 route_strategy 排好序的可用模型（含降级链），最多由 fallbackMaxDepth 截断 */
    private List<AiModelConfig> candidates;

    /** 实际采用的路由策略 */
    private Integer routeStrategy;

    /** 是否命中灰度实验 */
    private Boolean gray;

    /** 灰度分组：A 对照组、B 灰度组 */
    private String grayGroup;

    private Long grayExperimentId;

    /** 本次实际使用的分桶维度 */
    private String grayBucketKey;

    /** 分桶输入值快照，写入调用日志便于复盘归因 */
    private String bucketValue;

    /** PROMPT 类灰度实验覆盖的提示词编号，0 表示不覆盖 */
    private Long promptIdOverride;

    /** 是否由候选表提供候选（false 表示回落解析旧 fallback_model_ids 字符串） */
    private Boolean fromCandidateTable;

}
