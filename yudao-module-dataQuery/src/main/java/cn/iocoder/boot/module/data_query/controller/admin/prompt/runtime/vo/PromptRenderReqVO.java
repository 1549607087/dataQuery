package cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 管理后台 - S4 提示词运行时渲染 Request VO
 *
 * 与「编辑态调试渲染」不同：这里锁定 ai_prompt.current_version_id 对应的
 * ai_prompt_version 只读快照，不使用编辑态的 ai_prompt_variable，保证可复现、可回滚、可 diff。
 *
 * 刻意不接受 tenantId / promptId / 模板正文：模板按 sceneCode 由服务端选择
 * （租户覆盖优先、平台 0 兜底），防止前端指定他人模板或绕过已发布版本。
 */
@Schema(description = "管理后台 - S4 提示词运行时渲染 Request VO")
@Data
public class PromptRenderReqVO {

    @Schema(description = "场景编码，如 SQL_GEN；不传默认 SQL_GEN")
    @NotBlank(message = "提示词场景编码不能为空")
    @Size(max = 64, message = "场景编码长度不能超过 64")
    private String sceneCode = "SQL_GEN";

    @Schema(description = "数据源编号：提供时自动注入 dialect / sql_rules / identifier_quote / quote_all")
    private Long datasourceId;

    @Schema(description = "用户问题，注入 {{question}}")
    @Size(max = 500, message = "问题长度不能超过 500 字符")
    private String question;

    @Schema(description = "Schema 文本，注入 {{schema}}；不传时若提供数据源则自动做一次 S3 召回")
    @Size(max = 24000, message = "Schema 文本长度不能超过 24000 字符")
    private String schema;

    @Schema(description = "扩展变量（会与内置变量合并；内置变量优先级更高，避免前端伪造 dialect）")
    private Map<String, String> variables;

    @Schema(description = "是否在缺少 schema 时自动执行 S3 召回（需要同时提供 datasourceId 与 question）")
    private Boolean recallSchema = false;

    @Schema(description = "自动召回时的 Top-K 表数量")
    private Integer topK;

    /**
     * RAG 检索上下文，注入 {{context}}。
     *
     * <p>只由服务端在知识库问答链路中 set，{@code @JsonIgnore} 保证即使有人把它当请求体，
     * 也无法从 JSON 传入，杜绝前端伪造检索证据。
     */
    @JsonIgnore
    private String ragContext;

    /** RAG 历史对话，注入 {{history}}；同样只允许服务端 set */
    @JsonIgnore
    private String ragHistory;

}
