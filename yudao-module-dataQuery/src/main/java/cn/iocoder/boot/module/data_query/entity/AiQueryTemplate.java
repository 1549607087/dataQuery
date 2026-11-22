package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 查询模板（ai_query_template）
 *
 * 问数运行时 4 张表（P2-B）全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_query_template")
@Data
public class AiQueryTemplate {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 模板名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 模板编码，租户内唯一，供程序引用
     */
    @TableField(value = "code")
    private String code;

    /**
     * 模板分类，如 销售 / 财务 / 运营
     */
    @TableField(value = "category")
    private String category;

    /**
     * 参数化问题模板文本，含占位符
     */
    @TableField(value = "question_template")
    private String questionTemplate;

    /**
     * sql_template 仅存储参数化模板文本，绝不在此执行；P4 校验器与只读执行器落地后才允许被引用
     */
    @TableField(value = "sql_template")
    private String sqlTemplate;

    /**
     * 参数定义 JSON：名称、类型、必填、默认值、校验规则
     */
    @TableField(value = "params_def")
    private String paramsDef;

    /**
     * 关联 ai_datasource.id，模板适用的数据源，0 表示通用
     */
    @TableField(value = "datasource_id")
    private Long datasourceId;

    /**
     * 是否启用：0 否、1 是
     */
    @TableField(value = "enabled")
    private Integer enabled;

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
