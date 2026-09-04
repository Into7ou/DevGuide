package com.techstack.agent.github;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GitHub 搜索接口响应（仅取 items 字段）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GithubSearchResponse(List<GithubRepoItem> items) {
}
