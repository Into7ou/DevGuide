package com.techstack.agent.security;

import java.io.IOException;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.techstack.agent.entity.User;
import com.techstack.agent.entity.UserToken;
import com.techstack.agent.mapper.UserMapper;
import com.techstack.agent.mapper.UserTokenMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 登录成功后：把 access token 加密存入 user_tokens，再重定向回前端。
 * token 落库失败只记日志、不阻断登录（绑定是增强功能，不是登录的硬依赖）。
 */
@Slf4j
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserMapper userMapper;
    private final UserTokenMapper userTokenMapper;
    private final TokenCipher tokenCipher;
    private final String frontendBaseUrl;

    public OAuth2SuccessHandler(OAuth2AuthorizedClientService authorizedClientService,
                                UserMapper userMapper, UserTokenMapper userTokenMapper, TokenCipher tokenCipher,
                                @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.authorizedClientService = authorizedClientService;
        this.userMapper = userMapper;
        this.userTokenMapper = userTokenMapper;
        this.tokenCipher = tokenCipher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            if (authentication instanceof OAuth2AuthenticationToken token) {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        token.getAuthorizedClientRegistrationId(), token.getName());
                if (client != null && client.getAccessToken() != null) {
                    storeToken(token.getPrincipal(), client);
                } else {
                    log.warn("登录成功但未获取到 authorized client，token 未落库（不影响登录）");
                }
            }
        } catch (Exception e) {
            log.warn("token 落库失败，本次登录继续", e);
        }
        response.sendRedirect(frontendBaseUrl);
    }

    private void storeToken(OAuth2User oauth2User, OAuth2AuthorizedClient client) {
        Object idAttr = oauth2User.getAttribute("id");
        if (idAttr == null) {
            log.warn("GitHub 用户信息缺少 id，跳过 token 绑定");
            return;
        }
        User user = userMapper.findByGithubId(Long.valueOf(idAttr.toString()));
        if (user == null) {
            log.warn("未找到 github_id={} 的本地用户，跳过 token 绑定", idAttr);
            return;
        }
        UserToken token = new UserToken();
        token.setUserId(user.getId());
        token.setAccessToken(tokenCipher.encrypt(client.getAccessToken().getTokenValue()));
        token.setTokenType(client.getAccessToken().getTokenType() == null
                ? null : client.getAccessToken().getTokenType().getValue());
        token.setScope(client.getAccessToken().getScopes() == null
                ? null : String.join(",", client.getAccessToken().getScopes()));
        if (client.getAccessToken().getExpiresAt() != null) {
            token.setExpiresAt(client.getAccessToken().getExpiresAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        userTokenMapper.upsertByUserId(token);
    }
}
