package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答引用的文档片段来源信息。
 */
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
