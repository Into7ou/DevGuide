package com.techstack.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;

import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.mapper.TechStackMapper;

/** 首页搜索 -> 真实动态发现 -> 入库边界，外部 API 使用固定数据。 */
class DiscoveryAdmissionRegressionTest {
    private static final String WRONG_URL = "https://github.com/binary-husky/gpt_academic/wiki/online";

    private final TechStackMapper mapper = mock(TechStackMapper.class);
    private final GithubService github = mock(GithubService.class);
    private final TavilySearchService search = mock(TavilySearchService.class);
    private final DeepSeekChatModel model = mock(DeepSeekChatModel.class);
    private static final String ASTRO = """
            {"status":"verified","canonicalName":"Astro","kind":"framework","category":"frontend",
            "description":"面向内容的网站框架","officialUrl":"https://astro.build"}
            """;
    private static final String VERIFIED = "{\"verified\":true,\"officialSourceIndex\":0}";

    private void replies(String... replies) {
        var stub = when(model.call(any(Prompt.class)));
        for (String reply : replies) {
            stub = stub.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
        }
    }

    private void sources(String... urls) {
        List<SearchService.SearchContent> results = java.util.Arrays.stream(urls)
                .map(url -> new SearchService.SearchContent("Astro", "Astro is the web framework for content-driven websites.", url, null))
                .toList();
        when(search.query(anyString())).thenReturn(() -> new SearchService.SearchResult(results));
    }

    private void verifyNoPromotion() {
        verify(mapper, never()).upsertDiscovered(any(), any(), any(), any(), any());
        verifyNoInteractions(github);
    }

    private TechStackService service() {
        when(github.topRepos(anyString())).thenReturn(List.of(new GithubRepoDto(
                "gpt_academic", "binary-husky/gpt_academic", "AI academic assistant", 10000,
                "Python", "https://github.com/binary-husky/gpt_academic", WRONG_URL,
                List.of("backend"), null)));
        return new TechStackService(mapper, github, new ObjectMapper(),
                new TechStackDiscoveryService(search, model));
    }

    @Test
    void cLanguageMustNotPromoteUnrelatedRepositoryHomepage() {
        replies("{\"category\":\"backend\"}");
        assertThat(service().getOverview("C语言").officialDocUrl())
                .isNotEqualTo(WRONG_URL).isEqualTo(TechStackDiscoveryService.C_OFFICIAL_URL);
        verify(github).topRepos("language:C");
        verifyNoInteractions(search);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void nonTechnologyMustNotBecomeStackEvenWhenSoftwareRepositoriesMatch() {
        replies("{\"status\":\"not_tech_stack\"}");
        assertThatThrownBy(() -> service().getOverview("红烧肉"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoPromotion();
        verifyNoInteractions(search);
    }

    @Test
    void genuineNewTechnologyUsesVerifiedSourceAndCanonicalName() {
        replies(ASTRO, VERIFIED);
        sources(WRONG_URL, "https://astro.build/");
        var overview = service().getOverview("astro");
        assertThat(overview.name()).isEqualTo("Astro");
        assertThat(overview.officialDocUrl()).isEqualTo("https://astro.build/");
        verify(mapper).upsertDiscovered("Astro", "https://astro.build/", "面向内容的网站框架",
                "frontend", "Astro in:name,description");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/binary-husky/gpt_academic/wiki/online",
            "https://astro.build.evil.example/docs", "https://learn-astro.example/docs",
            "https://astro.build@evil.example/docs", "javascript:alert(1)"})
    void noFallbackToUnrelatedOrSpoofedSearchResult(String url) {
        replies(ASTRO, VERIFIED);
        sources(url);
        assertThatThrownBy(() -> service().getOverview("Astro")).isInstanceOf(DiscoveryUnavailableException.class);
        verifyNoPromotion();
    }

    @ParameterizedTest
    @ValueSource(strings = {"backend", "{}", "{\"status\":\"uncertain\"}",
            "{\"status\":\"verified\",\"category\":\"backend\"}",
            "{\"status\":\"verified\"} trailing text"})
    void incompleteOrMalformedIdentityNeverPromotes(String reply) {
        replies(reply);
        assertThatThrownBy(() -> service().getOverview("未知实体"))
                .isInstanceOf(DiscoveryUnavailableException.class);
        verifyNoPromotion();
        verifyNoInteractions(search);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"verified\":false,\"officialSourceIndex\":-1}",
            "{\"verified\":true,\"officialSourceIndex\":1}",
            "{\"verified\":true,\"officialSourceIndex\":0.5}",
            "{\"verified\":\"true\",\"officialSourceIndex\":0}",
            "{\"verified\":true,\"officialDocUrl\":\"https://invented.example\"}"})
    void unverifiedOrInventedOfficialSourceNeverPromotes(String verdict) {
        replies(ASTRO, verdict);
        sources("https://astro.build/");
        assertThatThrownBy(() -> service().getOverview("Astro")).isInstanceOf(DiscoveryUnavailableException.class);
        verifyNoPromotion();
    }

    @Test
    void searchFailureNeverPromotesFromCategoryAlone() {
        replies(ASTRO);
        when(search.query(anyString())).thenThrow(new RuntimeException("timeout"));
        assertThatThrownBy(() -> service().getOverview("Astro")).isInstanceOf(DiscoveryUnavailableException.class);
        verifyNoPromotion();
    }

    @Test
    void modelFailureNeverPromotes() {
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("timeout"));
        assertThatThrownBy(() -> service().getOverview("Astro")).isInstanceOf(DiscoveryUnavailableException.class);
        verifyNoPromotion();
    }

    @Test
    void officialRepositoryWikiIsAllowedButOtherRepositoriesAreNot() {
        replies(ASTRO.replace("https://astro.build", "https://github.com/withastro/astro"), VERIFIED);
        sources(WRONG_URL, "https://github.com/withastro/astro/wiki");
        assertThat(service().getOverview("Astro").officialDocUrl()).isEqualTo("https://github.com/withastro/astro/wiki");
    }

    @Test
    void existingCanonicalRecordIsReusedAfterAliasRecognition() {
        replies(ASTRO, VERIFIED);
        sources("https://astro.build/");
        var existing = new com.techstack.agent.entity.TechStack();
        existing.setName("Astro");
        existing.setOfficialDocUrl("https://astro.build/");
        when(mapper.findByNameIgnoreCase("Astro")).thenReturn(existing);
        assertThat(service().getOverview("astrojs").name()).isEqualTo("Astro");
        verify(mapper, never()).upsertDiscovered(any(), any(), any(), any(), any());
    }

    @Test
    void oversizedInputIsRejectedBeforeDiscoveryInsteadOfTruncated() {
        assertThatThrownBy(() -> service().getOverview("x".repeat(101))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(search, mapper, github);
        verify(model, never()).call(any(Prompt.class));
    }
}
