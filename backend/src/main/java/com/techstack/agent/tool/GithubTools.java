package com.techstack.agent.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.github.GithubClient;
import com.techstack.agent.service.GithubService;

import lombok.extern.slf4j.Slf4j;

/**
 * GitHub 工具集（M6 Agent 可调用的 Function Calling 工具）。
 * 用 @Tool 注解暴露，可被 MethodToolCallbackProvider 转成 ToolCallback 注册进 ChatClient。
 */
@Slf4j
@Component
public class GithubTools {

    private final GithubService githubService;
    private final GithubClient githubClient;
    private final ObjectMapper objectMapper;

    public GithubTools(GithubService githubService, GithubClient githubClient, ObjectMapper objectMapper) {
        this.githubService = githubService;
        this.githubClient = githubClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索某技术栈在 GitHub 上 star 最高的 10 个开源仓库。
     */
    @Tool(description = "搜索某个技术栈在 GitHub 上 star 最高的前 10 个开源仓库，返回仓库名、star 数、语言、简介和链接")
    public String searchGithubRepos(@ToolParam(description = "技术栈名或 GitHub 搜索关键词，如 react、spring boot") String keyword) {
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
            log.warn("GitHub 搜索失败，errorType={}", e.getClass().getSimpleName());
            return "GitHub 搜索服务暂不可用（可能限流），请基于已有资料继续作答";
        }
    }

    /**
     * 抓取某仓库的 README 原文（供学习引导引用）。
     */
    @Tool(description = "获取指定 GitHub 仓库的 README 原文，参数 fullName 形如 facebook/react")
    public String fetchRepoReadme(@ToolParam(description = "仓库全名，形如 facebook/react") String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "fullName 不能为空";
        }
        try {
            String readme = githubClient.fetchReadme(fullName.trim());
            if (readme == null || readme.isBlank()) {
                return "未获取到 " + fullName + " 的 README";
            }
            // 截断过长内容，避免撑爆上下文
            int max = 6000;
            return readme.length() > max ? readme.substring(0, max) + "\n...(截断)" : readme;
        } catch (Exception e) {
            log.warn("抓取 README 失败，errorType={}", e.getClass().getSimpleName());
            return "抓取 " + fullName + " 的 README 失败，请基于其他资料继续作答";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("工具结果序列化失败，errorType={}", e.getClass().getSimpleName());
            return "[]";
        }
    }

    /** 去掉可能构造 GitHub 限定符的字符，降低 query 注入风险。 */
    private String sanitize(String keyword) {
        String q = keyword == null ? "" : keyword.replaceAll("[:'\"]", " ").trim();
        return q.isBlank() ? q : q + " in:name,description,topics";
    }

    /** 工具返回的精简仓库摘要（record 供 Jackson 序列化）。 */
    record RepoSummary(String fullName, int stars, String language, String description, String url) {
    }
}
