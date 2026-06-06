package com.demo.ragchat.dto;

public class KnowledgeBaseStatus {

    private String knowledgeBase;
    private int totalFiles;
    private int indexedFiles;
    private int pendingFiles;
    private int indexingFiles;
    private int failedFiles;
    private int staleFiles;
    private int knowledgeGaps;

    public KnowledgeBaseStatus() {
    }

    public KnowledgeBaseStatus(String knowledgeBase,
                               int totalFiles,
                               int indexedFiles,
                               int pendingFiles,
                               int indexingFiles,
                               int failedFiles,
                               int staleFiles,
                               int knowledgeGaps) {
        this.knowledgeBase = knowledgeBase;
        this.totalFiles = totalFiles;
        this.indexedFiles = indexedFiles;
        this.pendingFiles = pendingFiles;
        this.indexingFiles = indexingFiles;
        this.failedFiles = failedFiles;
        this.staleFiles = staleFiles;
        this.knowledgeGaps = knowledgeGaps;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getIndexedFiles() {
        return indexedFiles;
    }

    public void setIndexedFiles(int indexedFiles) {
        this.indexedFiles = indexedFiles;
    }

    public int getPendingFiles() {
        return pendingFiles;
    }

    public void setPendingFiles(int pendingFiles) {
        this.pendingFiles = pendingFiles;
    }

    public int getIndexingFiles() {
        return indexingFiles;
    }

    public void setIndexingFiles(int indexingFiles) {
        this.indexingFiles = indexingFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(int failedFiles) {
        this.failedFiles = failedFiles;
    }

    public int getStaleFiles() {
        return staleFiles;
    }

    public void setStaleFiles(int staleFiles) {
        this.staleFiles = staleFiles;
    }

    public int getKnowledgeGaps() {
        return knowledgeGaps;
    }

    public void setKnowledgeGaps(int knowledgeGaps) {
        this.knowledgeGaps = knowledgeGaps;
    }
}
