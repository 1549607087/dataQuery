package cn.iocoder.boot.module.data_query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

import cn.iocoder.boot.module.data_query.framework.crypto.AiSecretTypeHandler;

/**
 * 模型供应商（接入凭证）表
 * @TableName ai_model_provider
 */
@TableName(value = "ai_model_provider", autoResultMap = true)
@Data
public class AiModelProvider {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 供应商名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 供应商编码：openai/qwen/deepseek/ollama/vllm/azure 等
     */
    @TableField(value = "code")
    private String code;

    /**
     * 协议：OPENAI / OLLAMA / AZURE / CUSTOM
     */
    @TableField(value = "protocol")
    private String protocol;

    /**
     * API 基础地址
     */
    @TableField(value = "base_url")
    private String baseUrl;

    /**
     * API Key（加密存储，接口不回显）
     */
    @TableField(value = "api_key", typeHandler = AiSecretTypeHandler.class)
    private String apiKey;

    /**
     * 备用凭证（Azure / 阿里云等需要）
     */
    @TableField(value = "api_secret", typeHandler = AiSecretTypeHandler.class)
    private String apiSecret;

    /**
     * 自定义请求头，JSON 字符串
     */
    @TableField(value = "headers")
    private String headers;

    /**
     * 代理地址
     */
    @TableField(value = "proxy_url")
    private String proxyUrl;

    /**
     * 每日 Token 上限，0 表示不限
     */
    @TableField(value = "daily_token_limit")
    private Long dailyTokenLimit;

    /**
     * 月度预算（元），0 表示不限
     */
    @TableField(value = "monthly_budget")
    private BigDecimal monthlyBudget;

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
     * 租户编号
     */
    @TableField(value = "tenant_id")
    private Long tenantId;

    /**
     * 
     */
    @TableField(value = "creator")
    private String creator;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 
     */
    @TableField(value = "updater")
    private String updater;

    /**
     * 
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * 
     */
    @TableField(value = "deleted")
    private Integer deleted;
}