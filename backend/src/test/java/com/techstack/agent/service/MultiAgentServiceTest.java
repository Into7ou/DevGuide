package com.techstack.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.*;
import com.techstack.agent.service.ResearchAgent.Assessment;
import com.techstack.agent.tool.WebSearchTools;
import com.techstack.agent.tool.WebSearchTools.SearchHit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.scheduler.Schedulers;
import org.reactivestreams.Subscription;

class MultiAgentServiceTest {
    ResearchAgent research = mock(ResearchAgent.class);
    GuideAgent guide = mock(GuideAgent.class);
    RagKnowledgeService knowledge = mock(RagKnowledgeService.class);
    WebSearchTools web = mock(WebSearchTools.class);
    DocsFetcher fetcher = mock(DocsFetcher.class);
    MultiAgentService service = new MultiAgentService(research, guide, knowledge, web, fetcher, 2, 3);
    LearnRequest request = new LearnRequest("Java", "Controller 是什么？");

    static RagSource source(String id, String origin, String text) {
        return new RagSource(id, "Spring MVC", "https://docs.spring.io/test", text,
                List.of("Spring MVC"), "official", origin);
    }

    Assessment assessment(boolean sufficient, boolean more) {
        return new Assessment(sufficient, "", "以下适用于 Spring MVC", sufficient ? "" : "Service 的边界", "Spring MVC controller official docs", more);
    }

    @BeforeEach void setup() {
        when(knowledge.search(anyString())).thenReturn(List.of(source("", "local", "A controller handles requests.")));
        when(research.assess(any(), anyList(), anyList())).thenReturn(assessment(true, false));
        when(research.toJson(any())).thenAnswer(call -> new ObjectMapper().writeValueAsString(call.getArgument(0)));
        when(guide.answerStream(anyString())).thenReturn(Flux.just("Controller 接收请求。", "[S1]"));
    }

    @AfterEach void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    List<RagEvent> run(LearnRequest input) { return service.stream(input).collectList().block(Duration.ofSeconds(5)); }

    @Test void localEvidenceIsRetrievedBeforeAgentAssessmentAndNeedsNoWeb() {
        var events = run(request);
        var order = inOrder(knowledge, research, guide);
        order.verify(knowledge).search(contains("Controller"));
        order.verify(research).assess(eq(request), anyList(), anyList());
        order.verify(research).toJson(any());
        order.verify(guide).answerStream(contains("Spring MVC"));
        verifyNoInteractions(web, fetcher);
        assertEquals("status", events.getFirst().type());
        assertEquals("done", events.getLast().type());
        assertEquals(List.of(source("S1", "local", "A controller handles requests.")),
                events.stream().filter(e -> e.type().equals("sources")).findFirst().orElseThrow().data());
    }

    @Test void progressIsDeliveredWhileResearchBlocksAndSseRequestsOnAnotherThread() throws Exception {
        CountDownLatch assessing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch progress = new CountDownLatch(1);
        when(research.assess(any(), anyList(), anyList())).thenAnswer(call -> {
            assessing.countDown(); release.await(4, TimeUnit.SECONDS); return assessment(true, false);
        });
        var subscriber = new BaseSubscriber<RagEvent>() {
            @Override protected void hookOnSubscribe(Subscription subscription) { request(1); }
            @Override protected void hookOnNext(RagEvent value) {
                if (value.data() instanceof String message && message.startsWith("本地检索完成")) progress.countDown();
                Schedulers.parallel().schedule(() -> request(1));
            }
        };
        try {
            service.stream(request).subscribe(subscriber);
            assertTrue(assessing.await(2, TimeUnit.SECONDS));
            assertTrue(progress.await(1, TimeUnit.SECONDS), "资料判断尚未结束时就应显示本地检索完成");
        } finally { release.countDown(); subscriber.dispose(); }
    }

    @Test void blockingResearchRunsOnBoundedElasticWorker() {
        AtomicReference<String> worker = new AtomicReference<>();
        when(knowledge.search(anyString())).thenAnswer(call -> {
            worker.set(Thread.currentThread().getName());
            return List.of(source("", "local", "A controller handles requests."));
        });

        run(request);

        assertTrue(worker.get().startsWith("boundedElastic-"),
                "阻塞研究流程必须与事件线程隔离: " + worker.get());
    }

    @Test void authenticatedUserIsVisibleOnWorkerAndDoesNotLeakIntoNextRequest() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "developer", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        List<String> workerPrincipals = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(knowledge.search(anyString())).thenAnswer(call -> {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            workerPrincipals.add(current == null ? "anonymous" : current.getName());
            return List.of(source("", "local", "A controller handles requests."));
        });

        run(request);
        SecurityContextHolder.clearContext();
        run(request);

        assertEquals(List.of("developer", "anonymous"), workerPrincipals);
    }

    @Test void ambiguousQuestionAsksForClarificationAndKeepsContext() {
        when(research.assess(any(), anyList(), anyList())).thenReturn(
                new Assessment(false, "你问的是 Spring MVC 还是其他框架？", "Java 生态", "", "", false));
        var events = run(request);
        assertTrue(events.stream().anyMatch(e -> e.type().equals("clarification")));
        verifyNoInteractions(web, guide, fetcher);
        assertEquals("done", events.getLast().type());
    }

    void webRound() {
        when(web.search(anyString())).thenAnswer(call -> IntStream.range(0, 6)
                .mapToObj(i -> new SearchHit("Docs " + i, "search snippet", "https://docs.spring.io/page" + i)).toList());
        when(research.selectPages(any(), any(), anyList(), eq(3))).thenReturn(List.of(0, 1, 2, 3));
        when(fetcher.fetchPage(anyString())).thenAnswer(call -> new DocsFetcher.Page("Docs", call.getArgument(0), "Actual page text"));
        when(research.extract(any(), anyList())).thenReturn(List.of(source("", "web", "New evidence")));
    }

    @Test void eachQuestionIsBoundedToTwoSearchesAndThreePageFetchesPerRound() {
        when(research.assess(any(), anyList(), anyList())).thenReturn(assessment(false, false));
        webRound();
        var events = run(request);
        verify(web, times(2)).search(anyString());
        verify(fetcher, times(6)).fetchPage(anyString());
        assertEquals(Map.of("sufficient", false), events.getLast().data());
        verify(guide).answerStream(contains("Service 的边界"));
    }

    @Test void explicitFollowupSearchesAgainWithFreshBudgetDespiteLocallySufficientAnswer() {
        webRound();
        when(research.assess(any(), anyList(), anyList())).thenReturn(assessment(true, true));
        var followup = new LearnRequest("Java", "还有更多吗？", List.of(
                new LearnRequest.Turn(request.question(), "它接收请求 [S1]", List.of(source("S1", "local", "prior evidence")))));
        run(followup);
        run(followup);
        verify(web, times(2)).search(anyString());
        verify(knowledge, times(2)).search(contains("Controller"));
        verify(guide, times(2)).answerStream(contains("还有更多吗"));
    }

    @Test void onlyCitedWebExcerptsAreSelectedAndWritingDoesNotBlockAnswer() throws Exception {
        webRound();
        when(research.assess(any(), anyList(), anyList())).thenReturn(assessment(false, false), assessment(true, false));
        when(research.extract(any(), anyList())).thenReturn(List.of(source("", "web", "Used"), source("", "web", "Unused")));
        when(guide.answerStream(anyString())).thenReturn(Flux.just("依据本地和新资料 [S1] [S2]"));
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(research.selectForStorage(any(), anyList())).thenAnswer(call -> call.getArgument(1));
        when(knowledge.save(anyList())).thenAnswer(call -> {
            writing.countDown(); release.await(5, TimeUnit.SECONDS); return 1;
        });
        try {
            assertEquals("done", run(request).getLast().type());
            assertTrue(writing.await(2, TimeUnit.SECONDS));
            verify(research).selectForStorage(eq(request), eq(List.of(source("S2", "web", "Used"))));
        } finally { release.countDown(); }
    }

    @Test void failedWebStillProducesPartialAnswerAndFailedGenerationDoesNotCache() {
        when(research.assess(any(), anyList(), anyList())).thenReturn(assessment(false, false));
        when(web.search(anyString())).thenThrow(new IllegalStateException("offline"));
        assertEquals(Map.of("sufficient", false), run(request).getLast().data());
        when(guide.answerStream(anyString())).thenReturn(Flux.concat(Flux.just("半句"), Flux.error(new IllegalStateException("broken stream"))));
        assertEquals("error", run(request).getLast().type());
        verify(research, never()).selectForStorage(any(), anyList());
        verify(knowledge, never()).save(anyList());
    }

    @Test void requestKeepsOnlyLastThreeTurnsAndUnknownCitationIdsAreIgnored() {
        var history = IntStream.range(0, 5).mapToObj(i -> new LearnRequest.Turn("q" + i, "a", List.<RagSource>of())).toList();
        assertEquals("q2", new LearnRequest("Java", "more", history).history().getFirst().question());
        assertEquals(List.of(source("S1", "local", "original")), MultiAgentService.citedSources("[S1] [S999] [S1]",
                List.of(source("S1", "local", "original"), source("S2", "web", "unused"))));
    }
}
