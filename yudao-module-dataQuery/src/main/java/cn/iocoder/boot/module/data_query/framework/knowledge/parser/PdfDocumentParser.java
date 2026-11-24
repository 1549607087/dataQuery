package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF 解析器：复用 Spring AI 官方 {@link PagePdfDocumentReader}（底层 PDFBox 3.x）按页抽取文本，
 * 适配成下游统一的 {@link ParsedDocument}。解析与切块解耦：本类只负责「读懂 PDF」，
 * 不负责按多大尺寸切片——那是 ChunkSplitter 的事。
 *
 * <p>与 {@link SpringAiMarkdownParser} 同构：官方 Reader 只吐 {@code List<Document>}（每页一个），
 * 本类把它转成带 charStart/charEnd/pageNo 的结构块，再交给自研 ChunkSplitter 做结构优先切分。
 *
 * <p>适配要点：
 * <ol>
 *   <li>每页一个 Document → 一个 PARAGRAPH 块，pageNo 来自 metadata 的 {@code page_number}
 *       （2.0.1 常量 {@link PagePdfDocumentReader#METADATA_START_PAGE_NUMBER} 的实际值），
 *       并对老版的 {@code page_number} 字面量做兼容；</li>
 *   <li>用 {@link ExtractedTextFormatter} 左对齐，压缩 PDF 版面抽取固有的大片空格；
 *       页眉/页脚删行数默认 0——不同文档页眉位置不一，贸然删会误伤正文，需要时按租户配置；</li>
 *   <li>tidy 清洗：连续空白折叠成一个空格、3 行以上空行折叠成 2 行，避免空格污染向量、白烧 token；
 *       并剥掉每页末尾的孤立页码数字行（PDFBox 会把页脚页码当正文抽出，如「...转增股本。\n1\n\n」），
 *       否则这个数字会被切块器的跨页重叠吸附到下一页首个切片的开头；页码溯源本身来自 metadata，不受影响；</li>
 *   <li>所有页文本皆空 → 抛异常（疑似扫描件/图片型 PDF 未含文本层）。按项目红线「不伪造成功」，
 *       由上层落 parse_status=3 + 可读原因，绝不静默产出空文档；</li>
 *   <li>fullText 是「解析后全文」坐标，块间以单个 \n 分隔、charEnd 不含分隔符；
 *       前端不使用原文件高亮下标，下游切分与落库契约保持不变。</li>
 * </ol>
 *
 * <p>一期不做表格结构还原：官方 Reader 不输出表格语义，PDF 表格会作为页面文本的一部分保留，
 * 仍可被检索；表格的结构化叙述化（TABLE_NARRATE）留待接入版面坐标提取后扩展。
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    /** 页码元数据键（2.0.1 中 METADATA_START_PAGE_NUMBER 的实际值即 page_number） */
    private static final String META_PAGE_NUMBER = "page_number";
    /** 合并多页时的末页键，兼容 pagesPerDocument > 1 的配置 */
    private static final String META_END_PAGE_NUMBER = "end_page_number";

    /** 每页文本清洗时，连续空白折叠为单个空格 */
    private static final String WS_RUN = "[ \\t\\u00A0]+";
    /** 3 行及以上空行折叠为 2 行 */
    private static final String BLANK_LINES = "\\n{3,}";

    /**
     * 页脚印刷页码：独占最后一行、1~4 位纯数字（如「...正文\n78」）。
     * 仅匹配「末尾 + 前面有换行 + 1~4 位纯数字」三条件同时成立的行，避免误删正文中间的数字；
     * 目录里的页码（如「...264」后跟页脚「2」）只删末尾页脚「2」，正文「264」保留。
     * 页码溯源来自 metadata，剥掉文本里的数字不影响 pageNo。
     */
    private static final String PAGE_FOOTER_NUMBER = "\\n\\d{1,4}\\s*$";

    private final PdfDocumentReaderConfig readerConfig;

    public PdfDocumentParser() {
        this(0, 0);
    }

    /**
     * 供测试/扩展注入页眉页脚删除行数。默认 0 行：不同文档的页眉位置差异大，
     * 一律删首行会误删正文第一行，宁可保留噪声交给 tidy 清洗。
     */
    PdfDocumentParser(int topLinesToDelete, int bottomLinesToDelete) {
        ExtractedTextFormatter formatter = ExtractedTextFormatter.builder()
                .withLeftAlignment(true)
                .withNumberOfTopTextLinesToDelete(Math.max(0, topLinesToDelete))
                .withNumberOfBottomTextLinesToDelete(Math.max(0, bottomLinesToDelete))
                .build();
        this.readerConfig = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .withPageExtractedTextFormatter(formatter)
                .build();
    }

    @Override
    public boolean supports(String fileType) {
        return fileType != null && "PDF".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) throws Exception {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF 文件内容为空: " + fileName);
        }
        // PDF 是二进制格式，不走 Markdown 的「平台编码回写」技巧；Reader 内部按 PDF 自身编码解析。
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        List<Document> pages = new PagePdfDocumentReader(resource, readerConfig).get();

        StringBuilder full = new StringBuilder(4096);
        List<ParsedBlock> blocks = new ArrayList<>(pages.size());
        for (Document page : pages) {
            String text = tidy(page.getText());
            if (text.isEmpty()) {
                continue;
            }
            Integer pageNo = resolvePageNo(page.getMetadata());
            int start = full.length();
            full.append(text);
            int end = full.length();
            full.append('\n');
            blocks.add(ParsedBlock.builder()
                    .text(text)
                    .charStart(start)
                    .charEnd(end)
                    .pageNo(pageNo)
                    .headingPath("")
                    .kind("PARAGRAPH")
                    .build());
        }

        if (blocks.isEmpty()) {
            throw new IllegalStateException(
                    "PDF 未提取到任何文本层，疑似扫描件或图片型 PDF，请先 OCR: " + fileName);
        }
        log.info("[pdf-parser] 解析完成: fileName={}, pages={}, blocks={}, chars={}",
                fileName, pages.size(), blocks.size(), full.length());
        return ParsedDocument.builder()
                .fullText(full.toString())
                .blocks(blocks)
                .fileType("PDF")
                .build();
    }

    /** 页码读取：优先 2.0.1 的 page_number，兼容其它版本可能的别名 */
    private static Integer resolvePageNo(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object v = metadata.get(META_PAGE_NUMBER);
        if (v == null) {
            v = metadata.get(META_END_PAGE_NUMBER);
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 清洗版面文本：折叠连续空格与超长空行，trim 首尾 */
    static String tidy(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll(WS_RUN, " ")
                .replaceAll(BLANK_LINES, "\n\n")
                .strip();
        // 剥掉页脚印刷页码（独占最后一行的纯数字），它会被切块重叠带到下一页首个切片开头。
        // strip 前后各清理一次：页码行后可能残留空行，剥掉后正文末尾可能再次出现空白。
        cleaned = cleaned.replaceAll(PAGE_FOOTER_NUMBER, "").strip();
        return cleaned;
    }
}
