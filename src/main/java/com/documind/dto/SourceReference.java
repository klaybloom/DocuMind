package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {

    private int index;
    private String knowledgeBase;
    private String fileName;
    private String page;
    private String chunkId;
    private String text;
    private double score;
}
