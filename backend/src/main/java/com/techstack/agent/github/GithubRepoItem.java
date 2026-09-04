package com.techstack.agent.github;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.techstack.agent.dto.GithubRepoDto;

/**
 * GitHub 搜索响应中的单个仓库（snake_case 字段映射为 camelCase）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GithubRepoItem(
        String name,
        @JsonProperty("full_name") String fullName,
        String description,
        @JsonProperty("stargazers_count") int stargazersCount,
        String language,
        @JsonProperty("html_url") String htmlUrl,
        String homepage,
        List<String> topics,
        @JsonProperty("updated_at") String updatedAt) {

    GithubRepoDto toDto() {
        return new GithubRepoDto(name, fullName, description, stargazersCount, language, htmlUrl, homepage,
                topics == null ? List.of() : topics, updatedAt);
    }
}
