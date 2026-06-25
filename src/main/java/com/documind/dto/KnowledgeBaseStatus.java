package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseStatus {

    private String knowledgeBase;
    private int totalFiles;
    private int indexedFiles;
    private int pendingFiles;
    private int indexingFiles;
    private int failedFiles;
    private int staleFiles;
    private int knowledgeGaps;
}
