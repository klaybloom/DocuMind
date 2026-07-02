package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理员视角的知识库详情，包含成员和所有者信息。
 */
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
