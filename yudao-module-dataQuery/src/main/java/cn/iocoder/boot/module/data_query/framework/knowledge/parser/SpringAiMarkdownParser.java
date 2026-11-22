package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Markdown 解析器：使用 Spring AI 官方 {@link MarkdownDocumentReader}（底层 commonmark-java）。
 *
 * <p>官方 Reader 只做「语义分组」，产出 {@code List<Document>}：标题文字写进
 * metadata（{@code title} / {@code category=header_N}），正文在 {@code getText()}；
 * 它不做 token 切分，也不提供原文字符偏移。本类负责把它适配成下游统一的
 * {@link ParsedDocument}，再交给自研 {@code ChunkSplitter} 做结构优先 + 递归降级切分。
 *
 * <p>适配要点：
 * <ol>
 *   <li>用 header_N + title 维护标题栈，生成 headingPath；并把标题文字拼回正文，
 *       否则 Embedding 只用 content 会导致标题语义丢失；</li>
 *   <li>code_block 标记为 CODE 块；GFM 表格没有 GFM 扩展支持，保持普通文本，
 *       不生成 TABLE 块（因此不走表格 LLM 叙述化）；</li>
 *   <li>把各 Document 的文本重新拼成一份 fullText 并顺序编号 charStart/charEnd。
 *       这是「解析后全文」坐标，不再是磁盘原文件坐标——前端不使用原文高亮下标，
 *       下游切分与落库契约保持不变。</li>
 * </ol>
 */
@Slf4j
@Component
public class SpringAiMarkdownParser implements DocumentParser {

    private static final String CATEGORY = "category";
    private static final String TITLE = "title";
    private static final String HEADER_PREFIX = "header_";
    private static final String CODE_BLOCK = "code_block";

    /** 代码块块类型：仅作类型标记，ChunkSplitter 不改切分策略 */
    public static final String KIND_CODE = "CODE";

    private final MarkdownDocumentReaderConfig config;

    public SpringAiMarkdownParser() {
        // 官方默认配置：代码块/引用各自独立成 Document，水平线不切分。
        this.config = MarkdownDocumentReaderConfig.builder().build();
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null
                && ("MD".equalsIgnoreCase(fileType) || "MARKDOWN".equalsIgnoreCase(fileType));
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) throws Exception {
        byte[] bytes = content == null ? new byte[0] : content;
        String sourceText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (!sourceText.isEmpty() && sourceText.charAt(0) == '\uFEFF') {
            sourceText = sourceText.substring(1);
        }
        // 官方 Reader 内部用 new InputStreamReader(input)（默认平台编码），中文 Windows 下
        // 直接喂 UTF-8 字节会乱码。这里先按 UTF-8 解码，再按平台默认编码回写，
        // 让 Reader 用同一默认编码解码回来，等价于强制 UTF-8 读入。
        byte[] readerBytes = sourceText.getBytes(java.nio.charset.Charset.defaultCharset());
        ByteArrayResource resource = new ByteArrayResource(readerBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        List<Document> documents = new MarkdownDocumentReader(resource, config).get();

        String normalized = sourceText.replace("\r\n", "\n").replace('\r', '\n');
        List<HeadingInfo> headings = scanHeadings(normalized);
        String[] headingStack = new String[6];
        String headingPath = "";
        int headingCursor = 0;
        List<ParsedBlockWithMeta> withMeta = new ArrayList<>();

        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            String category = stringValue(metadata.get(CATEGORY));
            String title = stringValue(metadata.get(TITLE));
            String text = document.getText() == null ? "" : document.getText();

            if (category != null && category.startsWith(HEADER_PREFIX)) {
                Integer level = parseLevel(category);
                if (level != null && title != null && !title.isBlank()) {
                    HeadingInfo matched = matchHeading(headings, headingCursor, level, title);
                    if (matched != null) {
                        headingPath = matched.path;
                        headingCursor = headings.indexOf(matched) + 1;
                    } else {
                        // 官方 Reader 对连续标题只保留最后一个，这里按标题栈兜底
                        headingStack[level - 1] = title;
                        for (int i = level; i < headingStack.length; i++) {
                            headingStack[i] = null;
                        }
                        headingPath = joinHeading(headingStack);
                    }
                    // 标题文字必须进入 content，否则向量里没有该章节的语义
                    text = title + "\n" + text;
                }
            }

            if (text.isBlank()) {
                continue;
            }
            String kind = CODE_BLOCK.equals(category) ? KIND_CODE : "PARAGRAPH";
            withMeta.add(new ParsedBlockWithMeta(text, headingPath, kind));
        }

        // 重建「解析后全文」与顺序下标：块间用单个 \n 分隔，charEnd 不含分隔符
        StringBuilder full = new StringBuilder();
        List<ParsedBlock> blocks = new ArrayList<>(withMeta.size());
        for (ParsedBlockWithMeta item : withMeta) {
            int start = full.length();
            full.append(item.text);
            int end = full.length();
            blocks.add(ParsedBlock.builder()
                    .text(item.text)
                    .charStart(start)
                    .charEnd(end)
                    .headingPath(item.headingPath)
                    .kind(item.kind)
                    .build());
            full.append('\n');
        }

        return ParsedDocument.builder()
                .fullText(full.toString())
                .blocks(blocks)
                .fileType("MD")
                .build();
    }

    /**
     * 预扫原文标题，补全官方 Reader 丢弃的连续标题路径（如 # 章 紧跟 ## 节）。
     * 围栏代码块内的 # 行不算标题。
     */
    private static List<HeadingInfo> scanHeadings(String text) {
        List<HeadingInfo> result = new ArrayList<>();
        String[] stack = new String[6];
        boolean fenced = false;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                continue;
            }
            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') {
                level++;
            }
            if (level >= 1 && level <= 6 && level < line.length() && line.charAt(level) == ' ') {
                String title = line.substring(level + 1).trim();
                stack[level - 1] = title;
                for (int i = level; i < stack.length; i++) {
                    stack[i] = null;
                }
                result.add(new HeadingInfo(level, title, joinHeading(stack)));
            }
        }
        return result;
    }

    private static HeadingInfo matchHeading(List<HeadingInfo> headings, int from, int level, String title) {
        for (int i = from; i < headings.size(); i++) {
            HeadingInfo h = headings.get(i);
            if (h.level == level && h.title.equals(title)) {
                return h;
            }
        }
        return null;
    }

    private static Integer parseLevel(String category) {
        try {
            int level = Integer.parseInt(category.substring(HEADER_PREFIX.length()));
            return level >= 1 && level <= 6 ? level : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String joinHeading(String[] stack) {
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (s != null && !s.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" > ");
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }

    private static class HeadingInfo {
        private final int level;
        private final String title;
        private final String path;

        private HeadingInfo(int level, String title, String path) {
            this.level = level;
            this.title = title;
            this.path = path;
        }
    }

    private static class ParsedBlockWithMeta {
        private final String text;
        private final String headingPath;
        private final String kind;

        private ParsedBlockWithMeta(String text, String headingPath, String kind) {
            this.text = text;
            this.headingPath = headingPath;
            this.kind = kind;
        }
    }
}
