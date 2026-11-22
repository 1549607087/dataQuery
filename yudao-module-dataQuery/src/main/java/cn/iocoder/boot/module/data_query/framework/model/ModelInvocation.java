package cn.iocoder.boot.module.data_query.framework.model;

import cn.iocoder.boot.module.data_query.framework.chat.ChatCancelToken;
import lombok.Data;

import java.util.Map;

/**
 * 模型调用上下文
 *
 * 一次「智能问数」会产生 4~6 次 LLM 调用（意图 / 改写 / Schema 召回 / SQL 生成 / 修正 / 解读），
 * 它们共享同一个 traceId，但 sceneCode 不同，因此路由策略、超时、模型都可能不同。
 */
@Data
public class ModelInvocation {

    /** 全链路追踪 ID，为空时由治理层生成；重试与降级共用同一个 root traceId */
    private String traceId;

    /** 场景编码，对应 ai_model_scene_binding.scene_code */
    private String sceneCode;

    /** 渲染后的提示词正文 */
    private String prompt;

    /** 模板变量（供实现类做日志或二次渲染） */
    private Map<String, Object> variables;

    /** 会话编号 */
    private Long conversationId;

    /** 用户编号，灰度按 USER 分桶时使用 */
    private Long userId;

    /** 租户编号，灰度按 TENANT 分桶时使用 */
    private Long tenantId;

    /** 会话标识，灰度按 SESSION 分桶时使用；为空时退化为 conversationId */
    private String sessionId;

    /** 生效的单次调用超时（毫秒），由治理层按「场景覆盖 > 模型默认」解析后写入 */
    private Integer timeoutMs;

    /** 生效的提示词编号（灰度 PROMPT 实验时会被覆盖） */
    private Long promptId;

    /**
     * 调用方偏好的模型配置编号（对话页「选择模型」）。
     * 仅当该模型在当前场景候选集内才会被提升为首选；不在候选集时由路由器直接拒绝，
     * 不允许用它绕开场景绑定、启停状态与租户隔离。
     */
    private Long preferredModelConfigId;

    /**
     * 取消令牌（可选）。由问答编排链路注入：治理层在重试/降级前检查它，
     * 模型客户端把在途 HTTP 订阅注册进去以便取消时 dispose。
     * 非问答场景（如表格叙述、治理测试）为 null，行为与改造前完全一致。
     */
    private ChatCancelToken cancelToken;

    /**
     * 流式首 token 超时（毫秒）。为空时客户端取默认值 15000。
     *
     * <p>与 {@link #timeoutMs} 的区别：{@code timeoutMs} 是「整次调用总时长」，
     * 逐 token 流式下用它会导致长回答说到一半被硬砍，因此流式链路改用「首 token + 增量间隔」两段式。
     */
    private Integer streamFirstTokenMs;

    /** 流式相邻增量间隔超时（毫秒）。为空时客户端取默认值 15000；超时即判定流已死。 */
    private Integer streamIdleMs;

}
