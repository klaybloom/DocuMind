package com.documind.config;

import com.documind.repository.UserAccountRepository;
import com.documind.service.DatabaseUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置，定义登录、退出、接口权限和静态资源访问规则。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/health/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auth/me").permitAll()
                        .requestMatchers("/api/v1/admin/users/options").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/admin/users/**", "/api/v1/admin/users").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/knowledge-bases/**", "/api/v1/admin/knowledge-bases").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/files/knowledge-bases").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/files/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/v1/chat/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/", "/index.html", "/app.js", "/api.js", "/utils.js",
                                "/admin.html", "/admin.js",
                                "/style.css", "/favicon.ico").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic
                        .authenticationEntryPoint(noWwwAuthenticateEntryPoint())
                )
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserAccountRepository repository) {
        return new DatabaseUserDetailsService(repository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 返回 401 时不带 WWW-Authenticate 头，避免浏览器弹出原生 Basic 登录框。
     * 前端会使用自己的登录界面处理认证。
     */
    private AuthenticationEntryPoint noWwwAuthenticateEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未登录\"}");
        };
    }
}
