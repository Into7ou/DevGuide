package com.techstack.agent.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * GitHub API 配置（application.yml 中 github.* 前缀）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "github")
public class GithubProperties {

    /** GitHub API 基础地址 */
    private String apiBaseUrl = "https://api.github.com";

    /** 应用级 Token（仅搜公开仓库，来自 .env，不硬编码） */
    private String token = "";
}
