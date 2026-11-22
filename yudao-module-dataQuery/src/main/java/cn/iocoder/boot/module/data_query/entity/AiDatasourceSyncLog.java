package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源同步日志（ai_datasource_sync_log）
 *
 * 数据源侧 6 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_datasource_sync_log")
@Data
public class AiDatasourceSyncLog {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_datasource.id
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 任务批次号，同一批次的多条明细共用
     */
    @TableField(value = "task_no")
    private String taskNo;

    /**
     * 任务类型：1 连通性检测、2 全量元数据同步、3 增量元数据同步、4 结构变更检测、5 数据预览采样、6 枚举值采集
     */
    @TableField(value = "sync_type")
    private Integer syncType;

    /**
     * 触发方式：1 手动、2 定时、3 创建后自动、4 结构变更告警
     */
    @TableField(value = "trigger_type")
    private Integer triggerType;

    /**
     * 本次任务范围：schema / 表清单
     */
    @TableField(value = "scope")
    private String scope;

    /**
     * 开始时间
     */
    @TableField(value = "start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField(value = "end_time")
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    @TableField(value = "latency_ms")
    private Integer latencyMs;

    /**
     * 状态：0 进行中、1 成功、2 部分成功、3 失败
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 本次新增表数
     */
    @TableField(value = "table_added")
    private Integer tableAdded;

    /**
     * 本次结构变更表数
     */
    @TableField(value = "table_changed")
    private Integer tableChanged;

    /**
     * 本次源端删除表数
     */
    @TableField(value = "table_removed")
    private Integer tableRemoved;

    /**
     * 本次新增字段数
     */
    @TableField(value = "column_added")
    private Integer columnAdded;

    /**
     * 本次变更字段数
     */
    @TableField(value = "column_changed")
    private Integer columnChanged;

    /**
     * 本次删除字段数
     */
    @TableField(value = "column_removed")
    private Integer columnRemoved;

    /**
     * 本次新增 Join 路径数
     */
    @TableField(value = "relation_added")
    private Integer relationAdded;

    /**
     * 本次新增枚举映射数
     */
    @TableField(value = "enum_added")
    private Integer enumAdded;

    /**
     * 结构变更明细 JSON：表名/字段名/变更前后类型，管理端变更告警的数据来源
     */
    @TableField(value = "change_detail")
    private String changeDetail;

    /**
     * 任务前结构版本号
     */
    @TableField(value = "struct_version_before")
    private Integer structVersionBefore;

    /**
     * 任务后结构版本号
     */
    @TableField(value = "struct_version_after")
    private Integer structVersionAfter;

    /**
     * 错误码
     */
    @TableField(value = "error_code")
    private String errorCode;

    /**
     * 错误摘要，禁止记录完整密码与连接串
     */
    @TableField(value = "error_msg")
    private String errorMsg;

    /**
     * 操作人用户 ID，定时任务为 0
     */
    @TableField(value = "operator_id")
    private Long operatorId;

    /**
     * 操作人名称
     */
    @TableField(value = "operator_name")
    private String operatorName;

    /**
     * 租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致
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
