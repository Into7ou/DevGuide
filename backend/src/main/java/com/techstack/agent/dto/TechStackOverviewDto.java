package com.techstack.agent.dto;

import java.util.List;

/**
 * 技术栈详情：官方文档 + 关键页 + GitHub Top10 聚合。
 */
public record TechStackOverviewDto(
        String name,
        String description,
        String officialDocUrl,
        List<String> docKeyPages,
        String category,
        List<GithubRepoDto> topRepos) {
}
