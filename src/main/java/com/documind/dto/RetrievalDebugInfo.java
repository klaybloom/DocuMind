package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索调试信息，记录候选片段分数和筛选原因。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalDebugInfo {

    private List<CandidateDebug> allCandidates;
    private int usedCount;
    private String knowledgeBase;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateDebug {

        private String chunkId;
        private String fileName;
        private String knowledgeBase;
        private String text;
        private double score;
        private String matchType;
        private boolean usedInAnswer;
    }
}
