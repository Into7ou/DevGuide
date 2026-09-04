package com.techstack.agent.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.github.GithubClient;

/**
 * GitHub 业务服务：在 GithubClient 之上增加缓存（应对搜索限流）。
 */
@Service
public class GithubService {

    private final GithubClient githubClient;

    public GithubService(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    @Cacheable(value = "githubTopRepos", key = "#query")
    public List<GithubRepoDto> topRepos(String query) {
        return githubClient.searchTopRepos(query);
    }
}
