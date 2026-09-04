package com.techstack.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import com.techstack.agent.dto.LearnRequest;

class RagEvidenceTest {
    @Test void rejectsModelParaphraseAndBindsQuoteToActualFetchedPage() {
        var model = mock(DeepSeekChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"excerpts":[
                {"pageIndex":0,"excerpt":"A controller handles requests.","techStacks":["Spring MVC"],"sourceType":"official"},
                {"pageIndex":0,"excerpt":"Controllers are magic.","techStacks":["Java"],"sourceType":"official"},
                {"pageIndex":9,"excerpt":"A controller handles requests.","techStacks":["Java"],"sourceType":"official"}]}
                """)))));
        var agent = new ResearchAgent(model);
        var sources = agent.extract(new LearnRequest("Java", "Controller?"), List.of(new DocsFetcher.Page(
                "Actual title", "https://docs.spring.io/controller", "Intro. A controller handles requests. Details.")));
        assertEquals(1, sources.size());
        assertEquals("A controller handles requests.", sources.getFirst().excerpt());
        assertEquals("https://docs.spring.io/controller", sources.getFirst().url());
        assertEquals("Actual title", sources.getFirst().title());
    }

    @Test void crossStackRetrievalHasNoExactStackFilterAndRetainsOriginalSource() {
        var vector = mock(VectorStore.class);
        when(vector.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("Original",
                Map.of("source", "https://docs.spring.io/ref", "tech_stack", "Spring MVC", "type", "official_doc"))));
        var service = new RagKnowledgeService(vector, mock(JdbcTemplate.class));
        var result = service.search("Java Controller");
        var query = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vector).similaritySearch(query.capture());
        assertNull(query.getValue().getFilterExpression());
        assertEquals("local", result.getFirst().retrievedFrom());
        assertEquals(List.of("Spring MVC"), result.getFirst().techStacks());
        assertEquals("Original", result.getFirst().excerpt());
    }

    @Test void writebackSkipsDuplicatesAndCapsAtThreeWithoutDeletingAnything() {
        var vector = mock(VectorStore.class);
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any())).thenReturn(true, false, false);
        var service = new RagKnowledgeService(vector, jdbc);
        var sources = IntStream.range(0, 5).mapToObj(i -> MultiAgentServiceTest.source("S" + i, "web", "excerpt" + i)).toList();
        assertEquals(2, service.save(sources));
        verify(vector, times(2)).add(anyList());
        verifyNoMoreInteractions(vector);
    }
}
