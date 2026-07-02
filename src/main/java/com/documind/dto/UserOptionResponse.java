package com.documind.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下拉选择场景使用的轻量用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOptionResponse {

    private String username;
    private String role;
    private boolean enabled;
}
