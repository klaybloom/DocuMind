package com.documind.service;

import com.documind.model.UserAccount;
import com.documind.repository.UserAccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security 用户加载器，从数据库读取账号和角色。
 */
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public DatabaseUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + account.getRole()));
        // 管理员同时拥有普通用户角色，方便复用用户侧接口权限。
        if ("ADMIN".equals(account.getRole())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return User.withUsername(account.getUsername())
                .password(account.getPassword())
                .authorities(authorities)
                .disabled(!account.isEnabled())
                .build();
    }
}
