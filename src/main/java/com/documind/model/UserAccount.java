package com.documind.model;

import jakarta.persistence.*;

/**
 * 用户账号数据库实体，保存登录名、密码哈希、角色和可访问知识库。
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, length = 20)
    private String role; // ADMIN 或 USER。

    @Column(length = 500)
    private String knowledgeBases; // 逗号分隔；管理员为 null 时表示全部知识库。

    public UserAccount() {}

    public UserAccount(String username, String password, String role, String knowledgeBases) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.knowledgeBases = knowledgeBases;
    }

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getKnowledgeBases() { return knowledgeBases; }
    public void setKnowledgeBases(String knowledgeBases) { this.knowledgeBases = knowledgeBases; }
}
