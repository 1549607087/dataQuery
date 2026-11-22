package cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台 - S4 提示词运行时渲染 Response VO
 *
 * 输出最终渲染正文 + promptVersionId + content_hash（写进 ai_query_audit），
 * 以及变量使用情况，便于排障与审计复现。
 * 不回显 tenant_id、deleted、模板原始密文等内部字段。
 */
@Schema(description = "管理后台 - S4 提示词运行时渲染 Response VO")
@Data
public class PromptRenderRespVO {

    @Schema(description = "场景编码")
    private String sceneCode;

    @Schema(description = "命中的模板编号 / 锁定的版本快照编号 / 版本号 / 模板归属（0 平台，>0 租户覆盖）")
    private Long promptId;
    private Long promptVersionId;
    private Integer version;
    private Long templateTenantId;

    @Schema(description = "是否因租户模板不可用而回落到平台默认模板")
    private Boolean fallbackToPlatform;

    @Schema(description = "渲染后的提示词正文（供 S5 作为 prompt 传入 ModelInvocation）")
    private String content;

    @Schema(description = "渲染正文的 SHA-256，写进 ai_query_audit 用于复现")
    private String contentHash;

    @Schema(description = "模板快照的 content_hash（数据库已有值，用于与渲染正文哈希区分）")
    private String snapshotHash;

    @Schema(description = "Token 粗估（中文按字符、英文按 4 字符 1 token）")
    private Integer tokenEstimate;

    @Schema(description = "模板声明的变量 / 实际注入的变量 / 未登记但出现在正文里的变量 / 缺失的必填变量 / 未使用的变量")
    private List<String> declaredVariables = new ArrayList<>();
    private List<String> providedVariables = new ArrayList<>();
    private List<String> undeclaredVariables = new ArrayList<>();
    private List<String> missingVariables = new ArrayList<>();
    private List<String> unusedVariables = new ArrayList<>();

    @Schema(description = "自动召回时的 S3 信息：召回方式 / 是否降级 / 降级原因 / 命中表数")
    private String recallMode;
    private Boolean recallDegraded;
    private String recallDegradeReason;
    private Integer recallTableCount;

}
