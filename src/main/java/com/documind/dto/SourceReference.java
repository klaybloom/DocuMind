package com.documind.dto;

public class SourceReference {

    private int index;
    private String knowledgeBase;
    private String fileName;
    private String page;
    private String chunkId;
    private String text;
    private double score;

    public SourceReference() {
    }

    public SourceReference(int index,
                           String knowledgeBase,
                           String fileName,
                           String page,
                           String chunkId,
                           String text,
                           double score) {
        this.index = index;
        this.knowledgeBase = knowledgeBase;
        this.fileName = fileName;
        this.page = page;
        this.chunkId = chunkId;
        this.text = text;
        this.score = score;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
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

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
