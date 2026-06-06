package com.demo.ragchat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Value("${app.security.admin-username}")
    private String adminUsername;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.user-username:}")
    private String userUsername;

    @Value("${app.security.user-password:}")
    private String userPassword;

    @Value("${app.security.min-password-length:12}")
    private int minPasswordLength;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/health/**").hasRole("ADMIN")
                        .requestMatchers("/api/files/knowledge-bases").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/files/**").hasRole("ADMIN")
                        .requestMatchers("/api/chat/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.ico").authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        if (isBlank(adminUsername) || isBlank(adminPassword)) {
            throw new IllegalStateException("必须配置 DOCUMIND_ADMIN_USERNAME 和 DOCUMIND_ADMIN_PASSWORD");
        }
        validatePassword("DOCUMIND_ADMIN_PASSWORD", adminPassword);

        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        UserDetails admin = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "USER")
                .build();
        users.createUser(admin);

        if (isBlank(userUsername) != isBlank(userPassword)) {
            throw new IllegalStateException("DOCUMIND_USER_USERNAME 和 DOCUMIND_USER_PASSWORD 必须同时配置");
        }

        if (!isBlank(userUsername) && !isBlank(userPassword)) {
            if (adminUsername.trim().equalsIgnoreCase(userUsername.trim())) {
                throw new IllegalStateException("DOCUMIND_USER_USERNAME 不能和 DOCUMIND_ADMIN_USERNAME 相同");
            }
            validatePassword("DOCUMIND_USER_PASSWORD", userPassword);
            UserDetails user = User.withUsername(userUsername)
                    .password(passwordEncoder.encode(userPassword))
                    .roles("USER")
                    .build();
            users.createUser(user);
        }

        return users;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validatePassword(String name, String value) {
        int requiredLength = Math.max(8, minPasswordLength);
        if (value.trim().length() < requiredLength) {
            throw new IllegalStateException(name + " 长度不能少于 " + requiredLength + " 字符");
        }
    }
}
