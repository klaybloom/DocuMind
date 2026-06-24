package com.documind.dto;

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

    public DocumentFileInfo() {
    }

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

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(String lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public long getDaysSinceUpload() {
        return daysSinceUpload;
    }

    public void setDaysSinceUpload(long daysSinceUpload) {
        this.daysSinceUpload = daysSinceUpload;
    }
}
