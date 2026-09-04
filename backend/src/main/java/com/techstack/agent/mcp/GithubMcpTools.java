package com.techstack.agent.mcp;

import java.util.List;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.github.GithubClient;
import com.techstack.agent.service.GithubService;

import lombok.extern.slf4j.Slf4j;

/**
 * MCP Server 端工具：用 @McpTool 把 GitHub 能力暴露为 MCP 工具。
 * 由 McpServerAnnotationScanner 自动扫描并注册到 MCP Server（SSE 端点 /sse）。
 * 与 tool 包下的 @Tool 工具（供 ChatClient 直接调用）互补，用于演示 MCP 协议两端。
 */
@Slf4j
@Component
public class GithubMcpTools {

    private final GithubService githubService;
    private final GithubClient githubClient;
    private final ObjectMapper objectMapper;

    public GithubMcpTools(GithubService githubService, GithubClient githubClient, ObjectMapper objectMapper) {
        this.githubService = githubService;
        this.githubClient = githubClient;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "github_search_top_repos",
            description = "搜索某技术栈在 GitHub 上 star 最高的前 10 个开源仓库")
    public String searchTopRepos(@McpToolParam(description = "技术栈名或搜索关键词") String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "搜索关键词不能为空，请提供具体技术栈名";
        }
        String query = sanitize(keyword);
        try {
            List<GithubRepoDto> repos = githubService.topRepos(query);
            if (repos.isEmpty()) {
                return "未搜索到与「" + keyword + "」相关的仓库";
            }
            return toJson(repos.stream()
                    .map(r -> new RepoSummary(r.fullName(), r.stargazersCount(), r.language(), r.description(), r.htmlUrl()))
                    .toList());
        } catch (Exception e) {
            log.warn("MCP GitHub 搜索失败: {}", query, e);
            return "GitHub 搜索服务暂不可用（可能限流），请稍后重试";
        }
    }

    @McpTool(name = "github_fetch_readme",
            description = "获取指定 GitHub 仓库的 README 原文")
    public String fetchReadme(@McpToolParam(description = "仓库全名，形如 facebook/react") String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "fullName 不能为空";
        }
        try {
            String readme = githubClient.fetchReadme(fullName.trim());
            if (readme == null || readme.isBlank()) {
                return "未获取到 " + fullName + " 的 README";
            }
            int max = 6000;
            return readme.length() > max ? readme.substring(0, max) + "\n...(截断)" : readme;
        } catch (Exception e) {
            log.warn("MCP 抓取 README 失败: {}", fullName, e);
            return "抓取 " + fullName + " 的 README 失败，请稍后重试";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("MCP 工具结果序列化失败", e);
            return "[]";
        }
    }

    private String sanitize(String keyword) {
        String q = keyword == null ? "" : keyword.replaceAll("[:'\"]", " ").trim();
        return q.isBlank() ? q : q + " in:name,description,topics";
    }

    record RepoSummary(String fullName, int stars, String language, String description, String url) {
    }
}
