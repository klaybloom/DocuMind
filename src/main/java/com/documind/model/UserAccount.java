package com.documind.model;

import jakarta.persistence.*;

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
    private String role; // "ADMIN" or "USER"

    @Column(length = 500)
    private String knowledgeBases; // comma-separated, null = all for ADMIN

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
