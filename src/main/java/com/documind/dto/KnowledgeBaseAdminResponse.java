package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseAdminResponse {

    private String knowledgeBase;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private List<String> owners;
    private List<String> members;
    private KnowledgeBaseStatus status;
    private boolean manageable;
    private boolean owner;
}
