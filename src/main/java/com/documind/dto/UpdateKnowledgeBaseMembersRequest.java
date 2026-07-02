package com.documind.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新知识库成员的请求体。
 */
@Data
@NoArgsConstructor
public class UpdateKnowledgeBaseMembersRequest {

    @Size(max = 200, message = "访问用户数量不能超过200个")
    private List<@Size(max = 100, message = "用户名长度不能超过100字符") String> members;
}
