package cn.iocoder.boot.module.data_query.framework.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * OpenAI 兼容流式帧解析器（{@code text/event-stream} 单帧 → 结构化增量）。
 *
 * <p>独立成类的原因：这是流式链路里最容易出错、也最值得单测的一段。真实 HTTP 不好造
 * 「跨包半行 / 无 usage / 200 里塞错误」的响应，把纯字符处理抽出来后可以穷举覆盖。
 *
 * <p>需要容忍的四种现实情况：
 * <ol>
 *   <li><b>帧前缀不统一</b>：WebClient 用 SSE reader 时拿到的是已剥掉 {@code data:} 的载荷，
 *       但网关 Content-Type 写错（返回 text/plain）时拿到的是原始行，这里两种都接；</li>
 *   <li><b>结束标记</b>：{@code data: [DONE]}；</li>
 *   <li><b>usage 末帧</b>：开了 {@code stream_options.include_usage} 后，最后一帧 choices 为空、
 *       usage 有值；关了则根本没有 usage，由调用方按字符数估算；</li>
 *   <li><b>错误塞进 200 响应体</b>：{@code {"error":{...}}}，必须当失败抛出，不能当空增量吞掉。</li>
 * </ol>
 *
 * <p>另有两点刻意的取舍：
 * <ul>
 *   <li>{@code reasoning_content} 不下发 —— 思维链不是业务回答，混进正文会污染引用编号；</li>
 *   <li>非 JSON 帧直接忽略 —— 部分网关会插入 {@code : ping} 注释或代理错误页，不能因此中断整条流。</li>
 * </ul>
 */
final class StreamChunkParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DONE = "[DONE]";
    private static final String DATA_PREFIX = "data:";

    private StreamChunkParser() {
    }

    /** 解析一帧；无需处理（空帧 / 注释 / 非 JSON）时返回 null */
    static Chunk parse(String rawFrame) {
        if (rawFrame == null) {
            return null;
        }
        String frame = rawFrame.trim();
        if (frame.isEmpty() || frame.startsWith(":") || frame.startsWith("event:")
                || frame.startsWith("id:") || frame.startsWith("retry:")) {
            return null;
        }
        if (frame.startsWith(DATA_PREFIX)) {
            frame = frame.substring(DATA_PREFIX.length()).trim();
        }
        if (frame.isEmpty()) {
            return null;
        }
        if (DONE.equals(frame)) {
            Chunk done = new Chunk();
            done.setDone(true);
            return done;
        }

        JsonNode root;
        try {
            root = JSON.readTree(frame);
        } catch (Exception ex) {
            // 代理插入的错误页、半行残片：跳过，不中断整条流
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText(error.toString());
            throw new IllegalStateException("供应商返回错误：" + abbreviate(message, 200));
        }

        Chunk chunk = new Chunk();
        chunk.setRequestId(text(root, "id"));
        chunk.setModel(text(root, "model"));
        JsonNode usage = root.path("usage");
        if (usage.isObject()) {
            chunk.setPromptTokens(usage.path("prompt_tokens").asInt(0));
            chunk.setCompletionTokens(usage.path("completion_tokens").asInt(0));
            chunk.setTotalTokens(usage.path("total_tokens").asInt(0));
            chunk.setHasUsage(true);
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode delta = choices.get(0).path("delta");
            String content = text(delta, "content");
            if (StringUtils.hasText(content)) {
                chunk.setContent(content);
            }
        }
        return chunk;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 单帧解析结果 */
    static final class Chunk {

        /** 是否收到 [DONE] */
        private boolean done;
        /** 本帧增量正文；null 表示本帧没有正文（角色帧 / usage 帧） */
        private String content;
        private String requestId = "";
        private String model = "";
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private boolean hasUsage;

        boolean isDone() {
            return done;
        }

        void setDone(boolean done) {
            this.done = done;
        }

        String getContent() {
            return content;
        }

        void setContent(String content) {
            this.content = content;
        }

        String getRequestId() {
            return requestId == null ? "" : requestId;
        }

        void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        String getModel() {
            return model == null ? "" : model;
        }

        void setModel(String model) {
            this.model = model;
        }

        Integer getPromptTokens() {
            return promptTokens;
        }

        void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        Integer getCompletionTokens() {
            return completionTokens;
        }

        void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        Integer getTotalTokens() {
            return totalTokens;
        }

        void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        boolean hasUsage() {
            return hasUsage;
        }

        void setHasUsage(boolean hasUsage) {
            this.hasUsage = hasUsage;
        }
    }
}
