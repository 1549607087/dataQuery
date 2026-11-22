package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析结果：全文 + 结构块列表。
 *
 * fullText 是所有 block.text 按 charStart 顺序拼回的「解析后全文」，
 * slice(charStart, charEnd) 用于切块器按字符下标截取原文（含 overlap）。
 * 注意：fullText 与各 block 的下标必须来自同一套字符序列，否则溯源会错位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    /** 解析后全文（供切块与溯源高亮） */
    @Builder.Default
    private String fullText = "";

    /** 结构块列表，按 charStart 升序 */
    @Builder.Default
    private List<ParsedBlock> blocks = new ArrayList<>();

    /** 文件类型（大写） */
    private String fileType;

    /** 总字符数 */
    public int length() {
        return fullText == null ? 0 : fullText.length();
    }

    /** 按全文字符下标切片，越界时自动夹紧到 [0, length] */
    public String slice(int start, int end) {
        if (fullText == null || fullText.isEmpty()) {
            return "";
        }
        int s = Math.max(0, Math.min(start, fullText.length()));
        int e = Math.max(s, Math.min(end, fullText.length()));
        return fullText.substring(s, e);
    }
}
