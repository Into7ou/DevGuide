package com.techstack.agent.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;

/**
 * 验证 GitHub 搜索响应（snake_case）能正确反序列化并映射为 camelCase DTO。
 */
class GithubRepoItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesSnakeCaseAndMapsToDto() throws Exception {
        String json = """
                {"items": [
                  {"name": "react",
                   "full_name": "facebook/react",
                   "description": "The library for web UIs",
                   "stargazers_count": 234567,
                   "language": "JavaScript",
                   "html_url": "https://github.com/facebook/react",
                   "homepage": "https://react.dev",
                   "topics": ["react", "ui"],
                   "updated_at": "2025-01-01T00:00:00Z",
                   "unexpected_field": "ignored"}
                ]}
                """;

        GithubSearchResponse response = mapper.readValue(json, GithubSearchResponse.class);

        assertThat(response.items()).hasSize(1);
        GithubRepoDto dto = response.items().get(0).toDto();
        assertThat(dto.name()).isEqualTo("react");
        assertThat(dto.fullName()).isEqualTo("facebook/react");
        assertThat(dto.stargazersCount()).isEqualTo(234567);
        assertThat(dto.htmlUrl()).isEqualTo("https://github.com/facebook/react");
        assertThat(dto.homepage()).isEqualTo("https://react.dev");
        assertThat(dto.topics()).containsExactly("react", "ui");
    }

    @Test
    void mapsNullHomepageToNull() throws Exception {
        String json = """
                {"items": [
                  {"name": "x", "full_name": "o/x", "stargazers_count": 0,
                   "html_url": "https://github.com/o/x", "topics": []}
                ]}
                """;
        GithubSearchResponse response = mapper.readValue(json, GithubSearchResponse.class);
        assertThat(response.items().get(0).toDto().homepage()).isNull();
    }
}
