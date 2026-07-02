package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * RAG 服务内部回答结果，包含最终答案、来源和调试信息。
 */
@Data
@NoArgsConstructor
public class RagAnswer {

    private String answer;
    private List<SourceReference> sources;
    private boolean fromDocuments;
    private RetrievalDebugInfo debugInfo;

    public RagAnswer(String answer, List<SourceReference> sources, boolean fromDocuments) {
        this.answer = answer;
        this.sources = sources == null ? Collections.emptyList() : sources;
        this.fromDocuments = fromDocuments;
    }
}
