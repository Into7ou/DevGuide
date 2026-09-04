package com.techstack.agent.dto;

import java.util.List;

/**
 * GitHub 仓库信息（对外输出，camelCase）。
 */
public record GithubRepoDto(
        String name,
        String fullName,
        String description,
        int stargazersCount,
        String language,
        String htmlUrl,
        String homepage,
        List<String> topics,
        String updatedAt) {
}
