package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本解析器（仅 TXT）。
 *
 * <p>Markdown 已改由 {@link SpringAiMarkdownParser} 使用官方 Spring AI Reader 处理，
 * 本类只保留 TXT 的逐行分段：规范化 CRLF 后的 fullText 是唯一字符序列，
 * 每个 block 的 text 与 charStart/charEnd 直接由它按区间截取。
 */
@Component
public class TxtMarkdownParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return fileType != null && "TXT".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) {
        String text = content == null ? "" : new String(content, StandardCharsets.UTF_8);
        String full = text.replace("\r\n", "\n").replace('\r', '\n');

        List<ParsedBlock> blocks = new ArrayList<>();
        int pos = 0;
        int paragraphStart = -1;
        while (pos <= full.length()) {
            int nl = full.indexOf('\n', pos);
            int end = nl < 0 ? full.length() : nl;
            String line = full.substring(pos, end);
            boolean lastLine = nl < 0;

            if (line.trim().isEmpty()) {
                if (paragraphStart >= 0) {
                    blocks.add(makeBlock(full, paragraphStart, pos));
                    paragraphStart = -1;
                }
            } else if (paragraphStart < 0) {
                paragraphStart = pos;
            }

            if (lastLine) {
                break;
            }
            pos = nl + 1;
        }
        if (paragraphStart >= 0) {
            blocks.add(makeBlock(full, paragraphStart, full.length()));
        }

        return ParsedDocument.builder()
                .fullText(full)
                .blocks(blocks)
                .fileType("TXT")
                .build();
    }

    /** 从全文按 [start,end) 截取建块，保证 text 与下标严格对应 */
    private ParsedBlock makeBlock(String full, int start, int end) {
        return ParsedBlock.builder()
                .text(full.substring(start, end))
                .charStart(start)
                .charEnd(end)
                .build();
    }
}
