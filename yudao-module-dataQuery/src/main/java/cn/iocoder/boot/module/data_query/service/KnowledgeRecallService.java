package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeRecallResult;

/**
 * 知识库切片混合召回 Service（P9.2 问答侧）
 *
 * <p>两路召回（向量 / 关键词）+ RRF 融合 + Token 预算裁剪，输出带 [1..K] 连续编号的上下文正文。
 * 只读，不落库、不调用生成模型；向量不可用时降级关键词路，两路皆空返回空结果而非异常。
 */
public interface KnowledgeRecallService {

    /**
     * @param baseId   知识库编号，必须属于当前租户
     * @param question 用户问题
     * @param topK     返回切片数；为空时取配置默认值
     * @param version  文档版本水位；null/非正数取当前生效版本，正数表示回退到 version&lt;=N 的最高版本
     */
    KnowledgeRecallResult recall(Long baseId, String question, Integer topK, Integer version);

    /** 默认当前生效版本（兼容既有调用点） */
    default KnowledgeRecallResult recall(Long baseId, String question, Integer topK) {
        return recall(baseId, question, topK, null);
    }
}
