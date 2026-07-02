package com.documind.service;

import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时根据环境变量初始化管理员和可选普通用户。
 */
@Component
@Order(2) // 在 JsonMigrationService 之后执行，保证旧数据先迁移完成。
public class UserAccountInitializer {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountInitializer.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-username:admin}")
    private String adminUsername;

    @Value("${app.security.admin-password:}")
    private String adminPassword;

    @Value("${app.security.user-username:}")
    private String userUsername;

    @Value("${app.security.user-password:}")
    private String userPassword;

    @Value("${app.security.user-knowledge-bases:default}")
    private String userKnowledgeBases;

    @Value("${app.security.min-password-length:12}")
    private int minPasswordLength;

    public UserAccountInitializer(UserAccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("必须配置 DOCUMIND_ADMIN_PASSWORD");
        }
        validatePassword("DOCUMIND_ADMIN_PASSWORD", adminPassword);

        // 创建或更新管理员账号。
        UserAccount admin = repository.findByUsername(adminUsername).orElseGet(() -> new UserAccount());
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setKnowledgeBases(null); // 管理员可访问全部知识库。
        repository.save(admin);
        logger.info("Admin account initialized: {}", adminUsername);

        // 如果配置了普通用户，则创建或更新普通用户账号。
        if (userUsername != null && !userUsername.isBlank()) {
            if (userPassword == null || userPassword.isBlank()) {
                throw new IllegalStateException("DOCUMIND_USER_USERNAME 和 DOCUMIND_USER_PASSWORD 必须同时配置");
            }
            if (adminUsername.trim().equalsIgnoreCase(userUsername.trim())) {
                throw new IllegalStateException("DOCUMIND_USER_USERNAME 不能和 DOCUMIND_ADMIN_USERNAME 相同");
            }
            validatePassword("DOCUMIND_USER_PASSWORD", userPassword);

            var existingUser = repository.findByUsername(userUsername);
            boolean newUser = existingUser.isEmpty();
            UserAccount user = existingUser.orElseGet(() -> new UserAccount());
            user.setUsername(userUsername);
            user.setPassword(passwordEncoder.encode(userPassword));
            user.setRole("USER");
            user.setEnabled(true);
            if (newUser) {
                user.setKnowledgeBases(userKnowledgeBases);
            }
            repository.save(user);
            logger.info("User account initialized: {}", userUsername);
        }
    }

    private void validatePassword(String name, String value) {
        int requiredLength = Math.max(8, minPasswordLength);
        if (value.trim().length() < requiredLength) {
            throw new IllegalStateException(name + " 长度不能少于 " + requiredLength + " 字符");
        }
    }
}
