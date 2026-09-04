package com.techstack.agent.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import com.techstack.agent.mapper.UserMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 登录时把 GitHub 用户信息原子 upsert 到 users 表（ON CONFLICT 合并，无并发竞态）。
 */
@Slf4j
@Component
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserMapper userMapper;

    public CustomOAuth2UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(request);

        Long githubId = toLong(oauth2User.getAttribute("id"));
        String login = oauth2User.getAttribute("login");
        String avatarUrl = oauth2User.getAttribute("avatar_url");

        if (githubId != null) {
            userMapper.upsertByGithubId(githubId, login, avatarUrl);
        } else {
            log.warn("GitHub 用户信息缺少 id，跳过本地用户 upsert");
        }
        return oauth2User;
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }
}
