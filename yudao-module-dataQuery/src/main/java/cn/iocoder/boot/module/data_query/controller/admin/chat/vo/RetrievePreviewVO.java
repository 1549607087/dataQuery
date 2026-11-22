package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeChunkHit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 检索调试台 VO：与 /ask 的 S2 同源，便于排查召回质量。 */
public final class RetrievePreviewVO {

    private RetrievePreviewVO() {
    }

    @Schema(description = "管理后台 - 检索调试请求 VO")
    @Data
    public static class Req {
        @Schema(description = "知识库编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "知识库编号不能为空")
        private Long baseId;

        @Schema(description = "调试问题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题长度不能超过 500 字符")
        private String question;

        @Schema(description = "返回切片数（可选，默认取服务端 topK）")
        private Integer topK;

        @Schema(description = "文档版本水位（可选，仅知识库有效）：回退到 version<=N 的最高版本；不传取当前生效版本")
        private Integer version;
    }

    @Schema(description = "管理后台 - 检索调试响应 VO")
    @Data
    public static class Resp {
        private String mode;
        private Boolean degraded;
        private String degradeReason;
        private Integer vectorCandidateCount;
        private Integer keywordCandidateCount;
        private Integer keptCount;
        private Boolean truncated;
        private Integer tokenEstimate;
        private String contextText;
        private Integer latencyMs;
        /** 是否实际执行了 Rerank 精排 */
        private Boolean rerankApplied;
        /** Rerank 未执行 / 降级原因 */
        private String rerankSkipReason;
        /** 本轮 Rerank 模型代号 */
        private String rerankModelCode;
        /** 本轮 Rerank 调用耗时（毫秒） */
        private Integer rerankLatencyMs;
        private List<KnowledgeChunkHit> items;
    }
}
