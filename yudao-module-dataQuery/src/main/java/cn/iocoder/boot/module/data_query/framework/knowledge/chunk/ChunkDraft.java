package cn.iocoder.boot.module.data_query.framework.knowledge.chunk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 切块草稿：ChunkSplitter 输出的中间产物，落库前由摄取服务补全 id/modelCode/hash/向量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDraft {

    /** 文档内从 0 递增的切片序号 */
    private int chunkIndex;

    /** 切片正文（已从 fullText 截取） */
    private String content;

    /** 近似 token 数 */
    private int tokenCount;

    /** 在解析后全文中的起始字符下标（含） */
    private int charStart;

    /** 在解析后全文中的结束字符下标（不含） */
    private int charEnd;

    /** PDF 页码；其它格式为 null */
    private Integer pageNo;

    /** 标题层级路径 */
    private String headingPath;

    /** 块类型：PARAGRAPH / TITLE / TABLE / TABLE_NARRATION，供落库标记与召回后排版使用 */
    @Builder.Default
    private String kind = "PARAGRAPH";
}
