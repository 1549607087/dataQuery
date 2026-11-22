package cn.iocoder.boot.module.data_query.framework.knowledge.version;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 文档版本链工具：逻辑文档标识规范化 + 切片内容哈希。
 *
 * 两个方法都必须与 DDL 回填脚本（nl2sql-knowledge-version.sql 步骤 3）保持一致：
 * - normalizeFileName：取路径最后一段 + trim + 转小写，兼容 Windows 反斜杠路径；
 * - computeContentHash：SHA-256(content + "|" + modelCode)，公式等于 KnowledgeIngestService
 *   历史上使用的 sha256Hex，否则存量切片无法参与增量复用。
 */
public final class KnowledgeVersionUtil {

    private KnowledgeVersionUtil() {
    }

    /**
     * 由文件名生成逻辑文档标识 docKey：取路径最后一段、去首尾空白、转小写。
     * 兼容 Windows 反斜杠路径（如浏览器上传的 C:\fakepath\报表.docx）。
     */
    public static String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String unix = fileName.replace('\\', '/');
        int idx = unix.lastIndexOf('/');
        String base = idx >= 0 ? unix.substring(idx + 1) : unix;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 切片内容哈希：SHA-256(content + "|" + modelCode)，与存量切片公式一致。
     * 换模型时 modelCode 变化导致哈希变化，自动触发重算，不会误复用旧向量。
     */
    public static String computeContentHash(String content, String modelCode) {
        String raw = content + "|" + modelCode;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算内容哈希", ex);
        }
    }
}
