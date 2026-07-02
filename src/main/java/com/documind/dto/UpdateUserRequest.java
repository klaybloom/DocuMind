package com.documind.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理员更新用户资料、角色和知识库权限的请求体。
 */
@Data
@NoArgsConstructor
public class UpdateUserRequest {

    @Pattern(regexp = "ADMIN|USER", message = "角色只能是 ADMIN 或 USER")
    private String role;

    private Boolean enabled;

    @Size(max = 50, message = "知识库数量不能超过50个")
    private List<@Size(max = 60, message = "知识库名称长度不能超过60字符")
            @Pattern(regexp = "\\*|[\\p{L}\\p{N}._-]+", message = "知识库名称只能是 * 或包含文字、数字、点、下划线和连字符")
                    String> knowledgeBases;
}
