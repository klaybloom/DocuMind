package com.documind.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新增知识库所有者的请求体。
 */
@Data
@NoArgsConstructor
public class AddKnowledgeBaseOwnersRequest {

    @NotEmpty(message = "负责人不能为空")
    @Size(max = 20, message = "负责人数量不能超过20个")
    private List<@Size(max = 100, message = "用户名长度不能超过100字符") String> owners;
}
