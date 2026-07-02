package com.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员重置用户密码的请求体。
 */
@Data
@NoArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "密码不能为空")
    @Size(max = 200, message = "密码长度不能超过200字符")
    private String password;
}
