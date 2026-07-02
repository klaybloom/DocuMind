package com.documind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档文件的前端展示信息，聚合文件元数据和索引状态。
 */
@Data
@NoArgsConstructor
public class DocumentFileInfo {

    private String knowledgeBase;
    private String fileName;
    private long sizeBytes;
    private String contentType;
    private String owner;
    private String uploadedBy;
    private String uploadedAt;
    private String lastIndexedAt;
    private String indexStatus;
    private int chunkCount;
    private String error;
    private String fileHash;
    private boolean stale;
    private long daysSinceUpload;

    public DocumentFileInfo(String knowledgeBase,
                            String fileName,
                            long sizeBytes,
                            String contentType,
                            String owner,
                            String uploadedBy,
                            String uploadedAt,
                            String lastIndexedAt,
                            String indexStatus,
                            int chunkCount,
                            String error,
                            String fileHash) {
        this.knowledgeBase = knowledgeBase;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.owner = owner;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.lastIndexedAt = lastIndexedAt;
        this.indexStatus = indexStatus;
        this.chunkCount = chunkCount;
        this.error = error;
        this.fileHash = fileHash;
    }
}
