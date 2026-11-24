package cn.iocoder.boot.module.data_query.framework.knowledge.parser;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 解析器分派器：按文件类型选 Parser。
 *
 * 已注册 TXT/MD/DOCX/PDF；DOC 在上传白名单里但解析尚未实现，
 * 这种消息消费时会明确失败（parse_status=3 + 可读原因），绝不静默成功。
 * PDF 由 {@link PdfDocumentParser} 处理（Spring AI PagePdfDocumentReader 按页抽文本）；
 * 扫描件 PDF 无文本层时该类抛错，同样落失败状态，不静默产出空文档。
 */
@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser getParser(String fileType) {
        if (fileType == null) {
            return null;
        }
        String type = fileType.toUpperCase();
        return parsers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElse(null);
    }

    public boolean isSupported(String fileType) {
        return getParser(fileType) != null;
    }
}
