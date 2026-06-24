package com.documind.dto;

import java.util.List;

public class RetrievalDebugInfo {

    private List<CandidateDebug> allCandidates;
    private int usedCount;
    private String knowledgeBase;

    public RetrievalDebugInfo() {
    }

    public RetrievalDebugInfo(List<CandidateDebug> allCandidates, int usedCount, String knowledgeBase) {
        this.allCandidates = allCandidates;
        this.usedCount = usedCount;
        this.knowledgeBase = knowledgeBase;
    }

    public List<CandidateDebug> getAllCandidates() {
        return allCandidates;
    }

    public void setAllCandidates(List<CandidateDebug> allCandidates) {
        this.allCandidates = allCandidates;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public static class CandidateDebug {

        private String chunkId;
        private String fileName;
        private String knowledgeBase;
        private String text;
        private double score;
        private String matchType;
        private boolean usedInAnswer;

        public CandidateDebug() {
        }

        public CandidateDebug(String chunkId, String fileName, String knowledgeBase,
                              String text, double score, String matchType, boolean usedInAnswer) {
            this.chunkId = chunkId;
            this.fileName = fileName;
            this.knowledgeBase = knowledgeBase;
            this.text = text;
            this.score = score;
            this.matchType = matchType;
            this.usedInAnswer = usedInAnswer;
        }

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getKnowledgeBase() {
            return knowledgeBase;
        }

        public void setKnowledgeBase(String knowledgeBase) {
            this.knowledgeBase = knowledgeBase;
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

        public String getMatchType() {
            return matchType;
        }

        public void setMatchType(String matchType) {
            this.matchType = matchType;
        }

        public boolean isUsedInAnswer() {
            return usedInAnswer;
        }

        public void setUsedInAnswer(boolean usedInAnswer) {
            this.usedInAnswer = usedInAnswer;
        }
    }
}
