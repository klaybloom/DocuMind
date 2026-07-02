package com.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建知识库的请求体。
 */
@Data
@NoArgsConstructor
public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 60, message = "知识库名称长度不能超过60字符")
    @Pattern(regexp = "[\\p{L}\\p{N}._-]+", message = "知识库名称只能包含文字、数字、点、下划线和连字符")
    private String name;

    @Size(max = 20, message = "负责人数量不能超过20个")
    private List<@Size(max = 100, message = "用户名长度不能超过100字符") String> owners;
}
