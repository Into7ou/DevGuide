package com.techstack.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.techstack.agent.security.CustomOAuth2UserService;
import com.techstack.agent.security.OAuth2SuccessHandler;

/**
 * Spring Security 配置：GitHub OAuth2 登录。
 * 当前业务接口暂未强制登录（M5 后续可收紧为 .anyRequest().authenticated()）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final AuthenticationSuccessHandler oauth2SuccessHandler;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          OAuth2SuccessHandler oauth2SuccessHandler) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 仅对 /api/** 关闭 CSRF（当前业务接口全为只读 GET，且为前后端分离的 JSON API；
                // 未来若加状态变更接口，应改为显式 requestMatchers 放行而非全站关闭）。
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(ui -> ui.userService(customOAuth2UserService))
                        .successHandler(oauth2SuccessHandler));
        return http.build();
    }
}
