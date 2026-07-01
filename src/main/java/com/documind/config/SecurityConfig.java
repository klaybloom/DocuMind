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
     * Returns a 401 without the "WWW-Authenticate: Basic" header,
     * preventing the browser from showing its native login dialog.
     * The frontend handles authentication via its own login page.
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
