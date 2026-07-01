package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountResponse {

    private Long id;
    private String username;
    private String role;
    private boolean enabled;
    private boolean allKnowledgeBases;
    private List<String> knowledgeBases;
}
