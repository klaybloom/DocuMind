package com.demo.ragchat.dto;

import java.util.Collections;
import java.util.List;

public class RagAnswer {

    private String answer;
    private List<SourceReference> sources;
    private boolean fromDocuments;
    private RetrievalDebugInfo debugInfo;

    public RagAnswer() {
    }

    public RagAnswer(String answer, List<SourceReference> sources, boolean fromDocuments) {
        this.answer = answer;
        this.sources = sources == null ? Collections.emptyList() : sources;
        this.fromDocuments = fromDocuments;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceReference> getSources() {
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = sources;
    }

    public boolean isFromDocuments() {
        return fromDocuments;
    }

    public void setFromDocuments(boolean fromDocuments) {
        this.fromDocuments = fromDocuments;
    }

    public RetrievalDebugInfo getDebugInfo() {
        return debugInfo;
    }

    public void setDebugInfo(RetrievalDebugInfo debugInfo) {
        this.debugInfo = debugInfo;
    }
}
