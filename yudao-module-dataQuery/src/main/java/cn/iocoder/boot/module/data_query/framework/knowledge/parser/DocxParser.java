package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DOCX 解析器（Apache POI XWPF，poi-ooxml 已在依赖中，零新增依赖）。
 *
 * 按文档 body 顺序遍历段落与表格：
 *  - 段落：识别标题样式（Heading 1~6 / 标题 1~6）维护 headingPath；正文作为 PARAGRAPH 块；
 *  - 表格：整表转为一个 TABLE 块，行内单元格用 " | " 分隔、行间换行。
 *
 * fullText 用 StringBuilder 顺序累积，block 的 charStart/charEnd 取累积前后长度，
 * 保证 text 与下标来自同一序列。
 */
@Component
public class DocxParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "DOCX".equalsIgnoreCase(fileType);
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder full = new StringBuilder(4096);
            List<ParsedBlock> blocks = new ArrayList<>();
            String[] headingStack = new String[6];
            XWPFStyles styles = doc.getStyles();

            for (IBodyElement bodyElement : doc.getBodyElements()) {
                if (bodyElement instanceof XWPFParagraph) {
                    XWPFParagraph p = (XWPFParagraph) bodyElement;
                    String text = p.getText() == null ? "" : p.getText();
                    int level = headingLevel(p, styles);
                    if (level > 0) {
                        headingStack[level - 1] = text.trim();
                        for (int i = level; i < headingStack.length; i++) {
                            headingStack[i] = null;
                        }
                        appendBlock(full, blocks, text, headingPath(headingStack), "TITLE");
                    } else if (!text.trim().isEmpty()) {
                        appendBlock(full, blocks, text, headingPath(headingStack), "PARAGRAPH");
                    }
                } else if (bodyElement instanceof XWPFTable) {
                    String tableText = tableText((XWPFTable) bodyElement);
                    if (!tableText.trim().isEmpty()) {
                        appendBlock(full, blocks, tableText, headingPath(headingStack), "TABLE");
                    }
                }
            }

            return ParsedDocument.builder()
                    .fullText(full.toString())
                    .blocks(blocks)
                    .fileType("DOCX")
                    .build();
        }
    }

    /** 追加一个块：text 写入 fullText，块的下标取追加前后长度 */
    private void appendBlock(StringBuilder full, List<ParsedBlock> blocks,
                             String text, String heading, String kind) {
        int start = full.length();
        full.append(text).append('\n');
        blocks.add(ParsedBlock.builder()
                .text(full.substring(start, start + text.length()))
                .charStart(start)
                .charEnd(start + text.length())
                .headingPath(heading == null ? "" : heading)
                .kind(kind)
                .build());
    }

    /** 整表转文本：行间换行，单元格用 " | " 分隔 */
    private String tableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText() == null ? "" : cell.getText().replace('\n', ' '));
            }
            sb.append(String.join(" | ", cells)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 识别标题层级 1~6。多路兜底，按可信度从高到低：
     *  1. 段落直接设置的 outlineLvl（w:pPr/w:outlineLvl，0-based，+1 得层级）；
     *  2. 段落所用样式的定义：样式 name 匹配 + 样式自带的 outlineLvl；
     *  3. 沿 basedOn 链上溯（自定义样式基于 heading 样式时）；
     *  4. styleId 本身形如 Heading1/标题1 的字符串兜底。
     *
     * 关键修正：POI 的 paragraph.getStyle()/getStyleID() 返回的是 styleId（如 "2"），
     * 不是样式名（如 "heading 2"）。中文 Word/WPS 的标题 styleId 常是数字，
     * 必须经 XWPFStyles.getStyle(styleId).getName() 拿到真实样式名才能匹配。
     */
    private int headingLevel(XWPFParagraph p, XWPFStyles styles) {
        // 1. 段落直接 outlineLvl（最高优先级，语义最直接）
        try {
            CTPPr pPr = p.getCTPPr();
            if (pPr != null && pPr.isSetOutlineLvl()) {
                int lvl = outlineLvlToLevel(pPr.getOutlineLvl().getVal());
                if (lvl > 0) {
                    return lvl;
                }
            }
        } catch (Exception ignored) {
            // 底层 XML 异常时继续走样式兜底
        }

        String styleId = p.getStyleID() == null ? "" : p.getStyleID();
        // 4. styleId 字符串兜底（如 "Heading1"、"标题 2"、"2" 之外可读的 id）
        int fromId = matchLevel(styleId);
        if (fromId > 0) {
            return fromId;
        }

        // 2 + 3. 样式定义 + basedOn 链
        if (styles != null && !styleId.isEmpty()) {
            Set<String> visited = new HashSet<>();
            String cur = styleId;
            for (int depth = 0; depth < 8 && cur != null && visited.add(cur); depth++) {
                XWPFStyle style = styles.getStyle(cur);
                if (style == null) {
                    break;
                }
                // 样式名（真正的 "heading 2" / "标题 2" 在这里）
                String name = style.getName();
                if (name != null && !name.isEmpty()) {
                    int fromName = matchLevel(name);
                    if (fromName > 0) {
                        return fromName;
                    }
                }
                // 样式定义里的 outlineLvl
                try {
                    CTPPrGeneral spPr = style.getCTStyle().getPPr();
                    if (spPr != null && spPr.isSetOutlineLvl()) {
                        int lvl = outlineLvlToLevel(spPr.getOutlineLvl().getVal());
                        if (lvl > 0) {
                            return lvl;
                        }
                    }
                } catch (Exception ignored) {
                    // 继续 basedOn 链
                }
                // 上溯父样式
                cur = style.getCTStyle().isSetBasedOn()
                        ? style.getCTStyle().getBasedOn().getVal() : null;
            }
        }
        return 0;
    }

    /** w:outlineLvl 的值（0-based，0=一级标题）转层级 1~6，越界返回 0 */
    private int outlineLvlToLevel(BigInteger val) {
        if (val == null) {
            return 0;
        }
        int lvl = val.intValue() + 1;
        return lvl >= 1 && lvl <= 6 ? lvl : 0;
    }

    /**
     * 从样式名/styleId 文本里匹配标题层级：
     * 英文 "Heading 1"/"heading2"、中文「标题 1」/「标题一」/「标题1」。
     */
    private int matchLevel(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        // 统一小写、全角空格转半角后去掉所有空格：兼容 "Heading 1"/"heading2"/"标题 2"/"标题二"
        String s = raw.toLowerCase(Locale.ROOT).replace('\u3000', ' ').replace(" ", "");
        // 英文 heading + 阿拉伯数字
        java.util.regex.Matcher en = java.util.regex.Pattern
                .compile("heading([1-6])").matcher(s);
        if (en.find()) {
            return Character.digit(en.group(1).charAt(0), 10);
        }
        // 中文「标题」+ 阿拉伯数字或中文数字
        java.util.regex.Matcher zh = java.util.regex.Pattern
                .compile("标题([1-6一二三四五六])").matcher(s);
        if (zh.find()) {
            return cnDigit(zh.group(1).charAt(0));
        }
        return 0;
    }

    /** 中文数字/阿拉伯数字字符转 1~6 */
    private int cnDigit(char c) {
        switch (c) {
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            default: return Character.digit(c, 10);
        }
    }

    private String headingPath(String[] stack) {
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
}
