package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识缺口展示信息，记录未命中文档的问题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGapInfo {

    private String id;
    private String knowledgeBase;
    private String question;
    private String sessionId;
    private String createdAt;
    private int occurrences;
    private String lastAskedAt;
}
