package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库状态汇总，供前端展示文件数和索引情况。
 */
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
