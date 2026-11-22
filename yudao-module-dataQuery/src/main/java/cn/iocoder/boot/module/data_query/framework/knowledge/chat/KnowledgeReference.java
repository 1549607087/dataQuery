package cn.iocoder.boot.module.data_query.framework.knowledge.chat;

import cn.iocoder.boot.module.data_query.framework.knowledge.recall.KnowledgeChunkHit;
import lombok.Data;

/**
 * 知识库问答引用溯源项（落 references_json，返回前端做高亮）
 *
 * <p>refNo 与回答中的 [n] 一一对应，其余字段来自被引用的切片，支持前端定位到
 * 文件名 / 页码 / 标题路径 / 原文字符区间。
 */
@Data
public class KnowledgeReference {

    private static final int CONTENT_MAX_LEN = 1000;

    /** 引用编号 1..K，与回答中的 [n] 对应 */
    private Integer refNo;

    private Long chunkId;

    private Long documentId;

    private String fileName;

    private Integer chunkIndex;

    /** 页码（TXT/MD 为 null） */
    private Integer pageNo;

    private String headingPath;

    private Integer charStart;

    private Integer charEnd;

    /** 切片内容（截断，避免 references_json 过大） */
    private String content;

    /** 逻辑文档标识（版本链） */
    private String docKey;

    /** 引用切片所属文档版本号，供溯源判断结论来自改版前/后 */
    private Integer version;

    public static KnowledgeReference from(KnowledgeChunkHit hit) {
        KnowledgeReference ref = new KnowledgeReference();
        ref.setRefNo(hit.getRefNo());
        ref.setChunkId(hit.getId());
        ref.setDocumentId(hit.getDocumentId());
        ref.setFileName(hit.getFileName());
        ref.setChunkIndex(hit.getChunkIndex());
        ref.setPageNo(hit.getPageNo());
        ref.setHeadingPath(hit.getHeadingPath());
        ref.setCharStart(hit.getCharStart());
        ref.setCharEnd(hit.getCharEnd());
        ref.setContent(clip(hit.getContent()));
        ref.setDocKey(hit.getDocKey());
        ref.setVersion(hit.getVersion());
        return ref;
    }

    private static String clip(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= CONTENT_MAX_LEN ? content : content.substring(0, CONTENT_MAX_LEN);
    }
}
