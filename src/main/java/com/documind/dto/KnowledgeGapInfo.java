package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
