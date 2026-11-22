package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析文档文本重写器：把表格块按「一对多」叙述映射展开，并重建 fullText 与全部块下标。
 *
 * 背景：表格经 LLM 叙述后，一张表会产出 N 段每段自带 20% 桥接前缀的描述文本。
 * 若把这些文本拼回一个块再靠切块器滑切，滑窗按字符走、不认段落边界，会把桥接结构腰斩。
 * 因此这里把一个 TABLE 块展开成 N 个 {@code TABLE_NARRATION} 块，每块是一个语义完整的叙述段；
 * 切块器会把叙述段与前后讲解正文合并成片，让「讲解 + 表格结论」落在同一 chunk。
 *
 * 不变量（与 DocxParser.appendBlock 同源）：对每个输出块
 * {@code fullText.substring(charStart, charEnd) == text}，块间以单个 '\n' 分隔（间隙不属于任何块）。
 * 这样 ChunkSplitter 的 {@code doc.slice(start,end)} 与前端溯源高亮都保持不变。
 */
@Component
public class DocumentTextRewriter {

    /** 叙述块类型标记，供 ChunkSplitter 识别并与前后讲解正文合并成片 */
    public static final String KIND_TABLE_NARRATION = "TABLE_NARRATION";

    /**
     * 按映射重建文档。
     *
     * @param doc          原始解析结果
     * @param replacements blockIndex -> 该表展开的 N 段叙述文本（每段已含桥接前缀）；
     *                     为 null / 空 / 不含该 key 时对应块原样保留（降级路径）
     * @return 新文档对象（不修改入参），fullText 与所有块下标已重建
     */
    public ParsedDocument rebuild(ParsedDocument doc, Map<Integer, List<String>> replacements) {
        if (doc == null || doc.getBlocks() == null || doc.getBlocks().isEmpty()) {
            return doc;
        }

        List<ParsedBlock> source = doc.getBlocks();
        List<ParsedBlock> newBlocks = new ArrayList<>(source.size() + 8);
        for (int i = 0; i < source.size(); i++) {
            ParsedBlock block = source.get(i);
            List<String> segments = replacements == null ? null : replacements.get(i);
            if (segments == null || segments.isEmpty()) {
                // 拷贝而非复用：下面会重设 charStart/charEnd，直接复用会改写入参对象
                newBlocks.add(ParsedBlock.builder()
                        .text(block.getText())
                        .charStart(block.getCharStart())
                        .charEnd(block.getCharEnd())
                        .pageNo(block.getPageNo())
                        .headingPath(block.getHeadingPath())
                        .kind(block.getKind())
                        .build());
                continue;
            }
            // 一对多展开：每段一个 TABLE_NARRATION 块，继承标题路径与页码
            for (String segment : segments) {
                if (segment == null || segment.isEmpty()) {
                    continue;
                }
                newBlocks.add(ParsedBlock.builder()
                        .text(segment)
                        .headingPath(block.getHeadingPath() == null ? "" : block.getHeadingPath())
                        .pageNo(block.getPageNo())
                        .kind(KIND_TABLE_NARRATION)
                        .build());
            }
        }

        // 顺序重建 fullText 与下标：append(text).append('\n')，charEnd 不含间隙换行
        StringBuilder full = new StringBuilder(doc.getFullText() == null ? 4096 : doc.getFullText().length());
        for (ParsedBlock block : newBlocks) {
            String text = block.getText() == null ? "" : block.getText();
            int start = full.length();
            full.append(text).append('\n');
            block.setCharStart(start);
            block.setCharEnd(start + text.length());
        }

        return ParsedDocument.builder()
                .fullText(full.toString())
                .blocks(newBlocks)
                .fileType(doc.getFileType())
                .build();
    }
}
