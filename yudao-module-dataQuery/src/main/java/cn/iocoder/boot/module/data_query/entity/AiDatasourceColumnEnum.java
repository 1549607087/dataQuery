package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段枚举映射（ai_datasource_column_enum）
 *
 * 数据源侧 6 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
 * 由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入时必须显式 setTenantId。
 */
@TableName(value = "ai_datasource_column_enum")
@Data
public class AiDatasourceColumnEnum {

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
     * 关联 ai_datasource_table.id
     */
    @TableField(value = "table_id")
    private Long tableId;

    /**
     * 关联 ai_datasource_column.id
     */
    @TableField(value = "column_id")
    private Long columnId;

    /**
     * 数据库中存储的原值
     */
    @TableField(value = "source_value")
    private String sourceValue;

    /**
     * 业务显示名，拼入提示词让 LLM 理解「1 = 已支付」
     */
    @TableField(value = "display_name")
    private String displayName;

    /**
     * 关联 system_dict_type.dict_type，空表示纯本地映射
     */
    @TableField(value = "dict_type")
    private String dictType;

    /**
     * 关联 system_dict_data.value
     */
    @TableField(value = "dict_value")
    private String dictValue;

    /**
     * 该枚举值的业务说明
     */
    @TableField(value = "description")
    private String description;

    /**
     * 来源：1 采样自动识别、2 手工录入、3 数据字典同步
     */
    @TableField(value = "source")
    private Integer source;

    /**
     * 采样中该值出现次数，用于判断有效性
     */
    @TableField(value = "sample_count")
    private Long sampleCount;

    /**
     * 状态：0 启用、1 停用
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
