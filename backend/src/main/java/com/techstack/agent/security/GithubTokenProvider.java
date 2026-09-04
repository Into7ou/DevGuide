package com.techstack.agent.security;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import com.techstack.agent.entity.User;
import com.techstack.agent.entity.UserToken;
import com.techstack.agent.github.GithubProperties;
import com.techstack.agent.mapper.UserMapper;
import com.techstack.agent.mapper.UserTokenMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 解析「当前该用哪个 GitHub Token」：已登录用户优先用其绑定 Token，
 * 绑定 Token 缺失 / 过期 / 解密失败时回退应用级 Token。
 */
@Slf4j
@Component
public class GithubTokenProvider {

    private final GithubProperties properties;
    private final UserMapper userMapper;
    private final UserTokenMapper userTokenMapper;
    private final TokenCipher tokenCipher;

    public GithubTokenProvider(GithubProperties properties, UserMapper userMapper,
                               UserTokenMapper userTokenMapper, TokenCipher tokenCipher) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.userTokenMapper = userTokenMapper;
        this.tokenCipher = tokenCipher;
    }

    public String currentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauthUser) {
            Object idAttr = oauthUser.getAttribute("id");
            if (idAttr != null) {
                User user = userMapper.findByGithubId(Long.valueOf(idAttr.toString()));
                if (user != null) {
                    UserToken token = userTokenMapper.findLatestByUserId(user.getId());
                    if (token != null) {
                        if (isExpired(token)) {
                            log.warn("用户 {} 的绑定 token 已过期，清理并回退应用级 token", user.getId());
                            userTokenMapper.deleteByUserId(user.getId());
                            return properties.getToken();
                        }
                        try {
                            return tokenCipher.decrypt(token.getAccessToken());
                        } catch (Exception e) {
                            log.warn("解密用户 token 失败，回退应用级 token");
                        }
                    }
                }
            }
        }
        return properties.getToken();
    }

    /**
     * 上游返回 401（token 被撤销/失效）时，清除当前用户绑定 token，使其后续回退应用级 token。
     *
     * @return 是否真的清除了一个用户 token（用于调用方决定是否重试）
     */
    public boolean invalidateCurrentUserToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauthUser) {
            Object idAttr = oauthUser.getAttribute("id");
            if (idAttr != null) {
                User user = userMapper.findByGithubId(Long.valueOf(idAttr.toString()));
                if (user != null) {
                    int deleted = userTokenMapper.deleteByUserId(user.getId());
                    if (deleted > 0) {
                        log.warn("用户 {} 的绑定 token 因 401 被清除，后续回退应用级 token", user.getId());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isExpired(UserToken token) {
        return token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now());
    }
}
