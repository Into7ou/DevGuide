package com.techstack.agent.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techstack.agent.entity.User;
import com.techstack.agent.mapper.UserMapper;

/**
 * 认证接口：查询当前登录用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;

    public AuthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            return Map.of("authenticated", false);
        }
        Object idAttr = oauthUser.getAttribute("id");
        User user = idAttr == null ? null : userMapper.findByGithubId(Long.valueOf(idAttr.toString()));
        // 用 HashMap 而非 Map.of：Map.of 不允许 null 值，login/avatar_url 为 null 时会 NPE。
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("username", oauthUser.getAttribute("login"));
        result.put("avatarUrl", oauthUser.getAttribute("avatar_url"));
        result.put("userId", user == null ? null : user.getId());
        return result;
    }
}
