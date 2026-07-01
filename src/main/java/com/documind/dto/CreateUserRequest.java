package com.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 100, message = "用户名长度不能超过100字符")
    @Pattern(regexp = "[A-Za-z0-9._@-]+", message = "用户名只能包含字母、数字、点、下划线、@和连字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 200, message = "密码长度不能超过200字符")
    private String password;

    @Pattern(regexp = "ADMIN|USER", message = "角色只能是 ADMIN 或 USER")
    private String role;

    private Boolean enabled;

    @Size(max = 50, message = "知识库数量不能超过50个")
    private List<@Size(max = 60, message = "知识库名称长度不能超过60字符")
            @Pattern(regexp = "\\*|[\\p{L}\\p{N}._-]+", message = "知识库名称只能是 * 或包含文字、数字、点、下划线和连字符")
                    String> knowledgeBases;
}
