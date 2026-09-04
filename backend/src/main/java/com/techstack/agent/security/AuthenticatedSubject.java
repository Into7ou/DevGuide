package com.techstack.agent.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 生成配额使用的稳定认证主体键，不把用户名或 Token 写入状态键。 */
public final class AuthenticatedSubject {

    private AuthenticatedSubject() { }

    public static String key(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("生成请求缺少认证用户");
        }
        if (authentication.getPrincipal() instanceof OAuth2User oauthUser) {
            Object githubId = oauthUser.getAttribute("id");
            if (githubId != null) return "github:" + githubId;
        }
        return "principal:" + authentication.getName();
    }
}
