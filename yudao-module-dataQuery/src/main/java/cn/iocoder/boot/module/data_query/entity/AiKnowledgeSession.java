package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库问答会话（ai_knowledge_session，P9.2）
 *
 * <p>租户私有表：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id；写入时必须显式 setTenantId。
 *
 * <p>一次多轮知识问答的上下文容器。与问数会话 {@link AiQuerySession} 的差异：
 * 用 base_id 绑定知识库（问数用 datasource_id），并直接内置 current_leaf_id 叶子指针
 * 支撑对话树（问数表是后续迁移追加的该列）。
 */
@TableName(value = "ai_knowledge_session")
@Data
public class AiKnowledgeSession {

    /** 会话状态：1 进行中 */
    public static final Integer STATUS_ACTIVE = 1;
    /** 会话状态：2 已结束 */
    public static final Integer STATUS_FINISHED = 2;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 提问用户 ID，只由登录态写入 */
    @TableField(value = "user_id")
    private Long userId;

    /** 绑定知识库 ai_knowledge_base.id；会话内不可切换 */
    @TableField(value = "base_id")
    private Long baseId;

    /** 会话标题，首问前 30 字自动生成 */
    @TableField(value = "title")
    private String title;

    /** 会话编号，租户内唯一（KS + 时间戳 + 6 位随机） */
    @TableField(value = "session_no")
    private String sessionNo;

    /** 场景编码，固定 RAG */
    @TableField(value = "scene_code")
    private String sceneCode;

    /** 状态：1 进行中 / 2 已结束 / 3 归档 */
    @TableField(value = "status")
    private Integer status;

    /** 消息条数，ASK 每轮 +2 */
    @TableField(value = "message_count")
    private Integer messageCount;

    /** 最近一次用户问题，会话列表预览用 */
    @TableField(value = "last_question")
    private String lastQuestion;

    /** 最近一次提问时间，会话列表排序键 */
    @TableField(value = "last_ask_time")
    private LocalDateTime lastAskTime;

    /** 当前激活分支末端消息 id，回溯唯一起点；NULL 表示尚无消息 */
    @TableField(value = "current_leaf_id")
    private Long currentLeafId;

    /** 首问链路编号 */
    @TableField(value = "trace_id")
    private String traceId;

    @TableField(value = "remark")
    private String remark;

    @TableField(value = "tenant_id")
    private Long tenantId;

    @TableField(value = "creator")
    private String creator;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "updater")
    private String updater;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(value = "deleted")
    private Integer deleted;

}
