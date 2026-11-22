package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问数会话（ai_query_session）
 *
 * 问数运行时 4 张表（P2-B）全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_query_session")
@Data
public class AiQuerySession {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 提问用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 关联 ai_datasource.id，本次会话绑定的数据源
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 会话标题，可由首问自动生成或用户修改
     */
    @TableField(value = "title")
    private String title;

    /**
     * 会话编号，租户内唯一，便于检索与对外引用
     */
    @TableField(value = "session_no")
    private String sessionNo;

    /**
     * 场景编码，关联 ai_prompt_scene.scene_code
     */
    @TableField(value = "scene_code")
    private String sceneCode;

    /**
     * 状态：1 进行中、2 已结束、3 归档
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 消息条数，含用户提问与助手回答
     */
    @TableField(value = "message_count")
    private Integer messageCount;

    /**
     * 最近一次用户问题，用于会话列表预览
     */
    @TableField(value = "last_question")
    private String lastQuestion;

    /**
     * 最近一次提问时间
     */
    @TableField(value = "last_ask_time")
    private LocalDateTime lastAskTime;

    /**
     * 当前激活分支末端消息 id（对话树模型，阶段 0 迁移新增列）。
     * 上下文重建 / 消息历史的唯一起点：从该叶子沿 parent_id 向上回溯激活路径。
     * NULL 表示会话尚无消息。指向 ai_query_message.id，但为软删表故不加物理外键。
     */
    @TableField(value = "current_leaf_id")
    private Long currentLeafId;

    /**
     * 链路追踪 ID，关联 ai_query_audit.trace_id
     */
    @TableField(value = "trace_id")
    private String traceId;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

    /**
     * 租户私有，全表不登记 ignore-tables，写入显式 setTenantId
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * creator
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * create_time
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * updater
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * update_time
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * deleted
     */
    @TableField(value = "deleted")
    private Integer deleted;

}
