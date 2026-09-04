package com.techstack.agent.github;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.security.GithubTokenProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * GitHub REST API 客户端（纯 HTTP，不含缓存；缓存由上层 GithubService 处理）。
 * 401 时：先清空当前用户绑定 token（若已登录），再用应用级 token 重试一次。
 */
@Slf4j
@Component
public class GithubClient {

    private final RestClient restClient;
    private final GithubProperties properties;
    private final GithubTokenProvider tokenProvider;

    public GithubClient(GithubProperties properties, GithubTokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        // 设置连接/读取超时，避免上游挂起时永久占用请求线程
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 按 star 降序搜索仓库，取前 10。
     *
     * @param query GitHub 搜索 query，如 "topic:react" 或 "react in:name,description"
     */
    public List<GithubRepoDto> searchTopRepos(String query) {
        try {
            return doSearchTopRepos(query);
        } catch (HttpClientErrorException.Unauthorized e) {
            return retryOnceAfterInvalidate(query, () -> doSearchTopRepos(query), e);
        }
    }

    private List<GithubRepoDto> doSearchTopRepos(String query) {
        GithubSearchResponse response = restClient.get()
                .uri(uri -> uri.path("/search/repositories")
                        .queryParam("q", query)
                        .queryParam("sort", "stars")
                        .queryParam("order", "desc")
                        .queryParam("per_page", 10)
                        .build())
                .headers(this::addAuthHeader)
                .retrieve()
                .body(GithubSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(GithubRepoItem::toDto).toList();
    }

    /**
     * 获取仓库 README 的原始文本（供 RAG 使用）。
     *
     * @param fullName 形如 "facebook/react"
     */
    public String fetchReadme(String fullName) {
        if (fullName == null || !fullName.matches("[\\w.-]+/[\\w.-]+")) {
            throw new IllegalArgumentException("仓库全名格式应为 owner/repo，如 facebook/react");
        }
        try {
            return doFetchReadme(fullName);
        } catch (HttpClientErrorException.Unauthorized e) {
            return retryOnceAfterInvalidate(fullName, () -> doFetchReadme(fullName), e);
        }
    }

    private String doFetchReadme(String fullName) {
        return restClient.get()
                .uri("/repos/{fullName}/readme", fullName)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                .headers(this::addAuthHeader)
                .retrieve()
                .body(String.class);
    }

    /**
     * 用户 token 被 GitHub 拒绝（401）时：清空该用户绑定 token，用应用级 token 重试一次；
     * 若没有可清理的用户 token（匿名访问或本就是应用级 token 失效），则原样抛出。
     */
    private <T> T retryOnceAfterInvalidate(String context, Supplier<T> retry,
                                           HttpClientErrorException.Unauthorized original) {
        if (tokenProvider.invalidateCurrentUserToken()) {
            try {
                return retry.get();
            } catch (HttpClientErrorException.Unauthorized retryEx) {
                log.warn("GitHub 401 重试仍失败（context={}），回退应用级 token 也无效", context);
                throw retryEx;
            }
        }
        throw original;
    }

    private void addAuthHeader(HttpHeaders headers) {
        String token = tokenProvider.currentToken();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }
}
