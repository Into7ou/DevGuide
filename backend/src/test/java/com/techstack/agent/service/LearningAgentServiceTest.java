package com.techstack.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.dto.LearningGuideResponse;
import com.techstack.agent.entity.TechStack;
import com.techstack.agent.github.GithubClient;
import com.techstack.agent.mapper.TechStackMapper;

/**
 * 学习引导 Agent 核心流程测试：引用提取 + 入库成功/失败/幂等 + 空检索降级 + 未知技术栈。
 */
class LearningAgentServiceTest {

    private TechStackMapper mapper;
    private GithubService githubService;
    private GithubClient githubClient;
    private DocsFetcher docsFetcher;
    private VectorStore vectorStore;
    private DeepSeekChatModel chatModel;
    private LearningAgentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TechStackMapper.class);
        githubService = mock(GithubService.class);
        githubClient = mock(GithubClient.class);
        docsFetcher = mock(DocsFetcher.class);
        vectorStore = mock(VectorStore.class);
        chatModel = mock(DeepSeekChatModel.class);
        service = new LearningAgentService(mapper, githubService, githubClient, docsFetcher,
                vectorStore, chatModel, new ObjectMapper());
    }

    private static TechStack unIngestedStack() {
        TechStack stack = new TechStack();
        stack.setId(1L);
        stack.setName("Test");
        stack.setDocKeyPages("[\"https://example.com/doc\"]");
        stack.setGithubSearchQuery("topic:test");
        return stack;
    }

    private static GithubRepoDto repo() {
        return new GithubRepoDto("repo", "owner/repo", "desc", 100, "Java",
                "https://github.com/owner/repo", null, List.of(), "2025-01-01");
    }

    @Test
    void extractCitationsReturnsDistinctNonNullSources() {
        List<Document> docs = List.of(
                new Document("a", Map.of("source", "https://a.com")),
                new Document("b", Map.of("source", "https://a.com")),
                new Document("c", Map.of()),
                new Document("d", Map.of("source", "https://b.com")));

        List<String> citations = LearningAgentService.extractCitations(docs);

        assertThat(citations).containsExactly("https://a.com", "https://b.com");
    }

    @Test
    void extractCitationsSkipsNonStringSources() {
        List<Document> docs = List.of(
                new Document("a", Map.of("source", 123)),
                new Document("b", Map.of("source", "https://b.com")));

        List<String> citations = LearningAgentService.extractCitations(docs);

        assertThat(citations).containsExactly("https://b.com");
    }

    @Test
    void ingestReturnsZeroAndDoesNotMarkWhenNothingFetched() {
        TechStack stack = unIngestedStack();
        stack.setDocKeyPages("[]");
        when(mapper.findByNameIgnoreCase("Test")).thenReturn(stack);
        when(githubService.topRepos("topic:test")).thenReturn(List.of());

        int chunks = service.ingest("Test");

        assertThat(chunks).isZero();
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(vectorStore, never()).add(any());
        verify(mapper, never()).markIngested(any(), any());
    }

    @Test
    void ingestStoresChunksAndMarksIngested() {
        when(mapper.findByNameIgnoreCase("Test")).thenReturn(unIngestedStack());
        when(docsFetcher.fetchText("https://example.com/doc"))
                .thenReturn("Getting started with Test framework. ".repeat(10));
        when(githubService.topRepos("topic:test")).thenReturn(List.of(repo()));
        when(githubClient.fetchReadme("owner/repo"))
                .thenReturn("This is a README with enough content to be chunked. ".repeat(10));

        int chunks = service.ingest("Test");

        assertThat(chunks).isPositive();
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(vectorStore).add(any());
        verify(mapper).markIngested(any(), any());
    }

    @Test
    void ingestDoesNotMarkWhenAddFails() {
        when(mapper.findByNameIgnoreCase("Test")).thenReturn(unIngestedStack());
        when(docsFetcher.fetchText("https://example.com/doc"))
                .thenReturn("Getting started with Test framework. ".repeat(10));
        when(githubService.topRepos("topic:test")).thenReturn(List.of(repo()));
        when(githubClient.fetchReadme("owner/repo"))
                .thenReturn("This is a README with enough content to be chunked. ".repeat(10));
        doThrow(new RuntimeException("add failed")).when(vectorStore).add(any());

        assertThatThrownBy(() -> service.ingest("Test"))
                .isInstanceOf(RuntimeException.class);

        verify(mapper, never()).markIngested(any(), any());
    }

    @Test
    void ingestSkipsWhenAlreadyIngested() {
        TechStack stack = unIngestedStack();
        stack.setRagIngestedAt(LocalDateTime.now());
        when(mapper.findByNameIgnoreCase("Test")).thenReturn(stack);

        int chunks = service.ingest("Test");

        assertThat(chunks).isZero();
        verify(vectorStore, never()).add(any());
        verify(mapper, never()).markIngested(any(), any());
    }

    @Test
    void learnReturnsFallbackWhenRetrievalEmpty() {
        TechStack stack = unIngestedStack();
        stack.setRagIngestedAt(LocalDateTime.now());
        when(mapper.findByNameIgnoreCase("Test")).thenReturn(stack);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        LearningGuideResponse resp = service.learn("Test", "question");

        assertThat(resp.answer()).contains("未检索到相关资料");
        assertThat(resp.citations()).isEmpty();
    }

    @Test
    void learnThrowsForUnknownStack() {
        when(mapper.findByNameIgnoreCase(anyString())).thenReturn(null);
        when(mapper.findByAlias(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.learn("Unknown", "q"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知技术栈");
    }
}
