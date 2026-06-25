package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

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
