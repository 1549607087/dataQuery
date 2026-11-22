package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

/**
 * 文档解析器 SPI。
 *
 * 每种文件类型一个实现，把字节流解析为统一的 ParsedDocument（全文 + 带位置的结构块）。
 * 解析与切块解耦：本接口只负责「读懂文件」，不负责按多大尺寸切片——那是 ChunkSplitter 的事。
 *
 * 新增文件格式只需实现一个 Parser 并注册到 DocumentParserFactory，不改切块/向量化链路。
 */
public interface DocumentParser {

    /** 该解析器是否支持此文件类型（大写扩展名，如 TXT / MD / DOCX） */
    boolean supports(String fileType);

    /**
     * 解析文件内容。
     *
     * @param content  文件原始字节
     * @param fileName 文件名（部分格式用于兜底判断）
     * @return 解析结果，含全文与结构块；空文档返回空 blocks、空 fullText
     * @throws Exception 文件损坏 / 格式不支持等解析失败
     */
    ParsedDocument parse(byte[] content, String fileName) throws Exception;
}
