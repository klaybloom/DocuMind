package com.documind.model;

import jakarta.persistence.*;

@Entity
@Table(name = "document_files", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"knowledgeBase", "fileName"})
})
/**
 * 文档文件数据库实体，记录文件归属、路径、大小和索引状态。
 */
public class DocumentFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String knowledgeBase;

    @Column(nullable = false)
    private String fileName;

    private long sizeBytes;

    private String contentType;

    @Column(length = 80)
    private String owner;

    @Column(length = 100)
    private String uploadedBy;

    private String uploadedAt;

    private String lastIndexedAt;

    @Column(length = 16)
    private String indexStatus;

    private int chunkCount;

    @Column(length = 500)
    private String error;

    @Column(length = 64)
    private String fileHash;

    protected DocumentFileEntity() {}

    public DocumentFileEntity(String knowledgeBase, String fileName, long sizeBytes, String contentType,
                              String owner, String uploadedBy, String uploadedAt,
                              String lastIndexedAt, String indexStatus, int chunkCount, String error) {
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
    }

    public Long getId() { return id; }

    public String getKnowledgeBase() { return knowledgeBase; }
    public void setKnowledgeBase(String knowledgeBase) { this.knowledgeBase = knowledgeBase; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getLastIndexedAt() { return lastIndexedAt; }
    public void setLastIndexedAt(String lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; }

    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
}
