package com.techstack.agent.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.techstack.agent.security.CustomOAuth2UserService;
import com.techstack.agent.security.OAuth2SuccessHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 配置：GitHub OAuth2 登录 + 生产 API 访问边界。
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
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                "/api/health", "/api/auth/me", "/api/auth/csrf",
                                "/api/v1/showcase/tech-stacks", "/api/v1/showcase/tech-stacks/**",
                                "/oauth2/**", "/login/**", "/error")
                        .permitAll()
                        .requestMatchers("/api/**", "/sse", "/mcp/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, failure) -> writeUnauthorized(response),
                                request -> request.getRequestURI().startsWith("/api/")
                                        || request.getRequestURI().equals("/sse")
                                        || request.getRequestURI().startsWith("/mcp/")))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(ui -> ui.userService(customOAuth2UserService))
                        .successHandler(oauth2SuccessHandler));
        return http.build();
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"authentication_required\"}");
    }
}
