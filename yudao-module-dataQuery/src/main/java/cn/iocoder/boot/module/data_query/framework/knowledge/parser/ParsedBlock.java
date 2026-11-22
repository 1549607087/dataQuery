package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析后的结构块：解析器输出的原子单位，切块器的输入。
 *
 * 一个 block 通常对应一个段落 / 标题段 / 表格 / PDF 的一页文本块。
 * 关键：携带它在「解析后全文」中的字符下标，供最终 chunk 溯源高亮原文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedBlock {

    /** 块正文 */
    private String text;

    /** 在解析后全文中的起始字符下标（含） */
    private int charStart;

    /** 在解析后全文中的结束字符下标（不含） */
    private int charEnd;

    /** PDF 页码（从 1 开始）；TXT/MD/DOCX 为 null */
    private Integer pageNo;

    /** 标题层级路径，如 "2.1 报销标准 > 交通费"；无标题为空串 */
    @Builder.Default
    private String headingPath = "";

    /** 块类型提示：TITLE / PARAGRAPH / TABLE / CODE 等，供切块策略微调（一期仅记录不强制使用） */
    @Builder.Default
    private String kind = "PARAGRAPH";

    /** 有效字符数（去掉空白后），用于判断是否空块 */
    public int effectiveLength() {
        return text == null ? 0 : text.trim().length();
    }
}
