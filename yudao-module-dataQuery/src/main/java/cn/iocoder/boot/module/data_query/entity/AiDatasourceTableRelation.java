package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表关系 / Join 路径（ai_datasource_table_relation）
 *
 * 数据源侧 6 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_datasource_table_relation")
@Data
public class AiDatasourceTableRelation {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 ai_datasource.id；Join 路径不跨数据源
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 左表，关联 ai_datasource_table.id
     */
    @TableField(value = "left_table_id")
    private Long leftTableId;

    /**
     * 右表，关联 ai_datasource_table.id
     */
    @TableField(value = "right_table_id")
    private Long rightTableId;

    /**
     * 左表物理名，冗余便于生成 SQL
     */
    @TableField(value = "left_table_name")
    private String leftTableName;

    /**
     * 右表物理名，冗余便于生成 SQL
     */
    @TableField(value = "right_table_name")
    private String rightTableName;

    /**
     * 连接类型：1 INNER、2 LEFT、3 RIGHT、4 FULL、5 CROSS
     */
    @TableField(value = "join_type")
    private Integer joinType;

    /**
     * 关联字段对 JSON 数组，如 [{\"left\":\"customer_id\",\"right\":\"id\"}]
     */
    @TableField(value = "join_pairs")
    private String joinPairs;

    /**
     * 额外 ON 条件表达式，必须经静态校验，禁止子查询与函数注入
     */
    @TableField(value = "join_condition")
    private String joinCondition;

    /**
     * 基数：1 一对一、2 一对多、3 多对一、4 多对多
     */
    @TableField(value = "cardinality")
    private Integer cardinality;

    /**
     * 路径代价 / 优先级，数值越小越优先推荐给 LLM
     */
    @TableField(value = "cost")
    private Integer cost;

    /**
     * 关系业务说明，如「订单归属客户」
     */
    @TableField(value = "description")
    private String description;

    /**
     * 来源：1 外键自动推断、2 手工配置、3 LLM 建议后人工审核
     */
    @TableField(value = "source")
    private Integer source;

    /**
     * 是否已用真实 SQL 验证可执行：0 否、1 是
     */
    @TableField(value = "verified")
    private Integer verified;

    /**
     * 是否启用：0 否、1 是
     */
    @TableField(value = "enabled")
    private Integer enabled;

    /**
     * 状态：0 正常、1 停用
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 排序
     */
    @TableField(value = "sort")
    private Integer sort;

    /**
     * 备注
     */
    @TableField(value = "remark")
    private String remark;

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
