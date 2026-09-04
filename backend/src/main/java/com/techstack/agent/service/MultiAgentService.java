package com.techstack.agent.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.techstack.agent.dto.LearnRequest;
import com.techstack.agent.dto.RagEvent;
import com.techstack.agent.dto.RagSource;
import com.techstack.agent.tool.WebSearchTools;
import com.techstack.agent.service.ResearchAgent.Assessment;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

/** 本地检索是必经步骤；联网预算按请求计算，Agent 只负责资料与覆盖程度判断。 */
@Slf4j
@Service
public class MultiAgentService {
    private static final Pattern CITATION = Pattern.compile("\\[(S\\d+)\\]");
    private final ResearchAgent research;
    private final GuideAgent guide;
    private final RagKnowledgeService knowledge;
    private final WebSearchTools web;
    private final DocsFetcher fetcher;
    private final int maxRounds;
    private final int maxPages;

    public MultiAgentService(ResearchAgent research, GuideAgent guide, RagKnowledgeService knowledge,
                             WebSearchTools web, DocsFetcher fetcher,
                             @Value("${app.rag.max-web-rounds:2}") int maxRounds,
                             @Value("${app.rag.max-pages-per-round:3}") int maxPages) {
        this.research = research;
        this.guide = guide;
        this.knowledge = knowledge;
        this.web = web;
        this.fetcher = fetcher;
        this.maxRounds = maxRounds;
        this.maxPages = maxPages;
    }

    public String run(LearnRequest request) {
        return stream(request).map(e -> {
            if (e.type().equals("error")) throw new IllegalStateException(e.data().toString());
            return e.type().equals("answer") || e.type().equals("clarification") ? e.data().toString() : "";
        }).collectList().map(parts -> String.join("", parts)).block();
    }

    public Flux<RagEvent> stream(LearnRequest request) {
        return Flux.<RagEvent>create(sink -> {
            try {
                List<RagSource> sources = new ArrayList<>();
                List<String> searches = new ArrayList<>();
                status(sink, "正在检索本地知识库…");
                try {
                    String history = request.history().stream().map(LearnRequest.Turn::question)
                            .reduce("", (a, b) -> a + "\n" + b);
                    addSources(sources, knowledge.search(request.techStack() + history + "\n" + request.question()));
                    status(sink, "本地检索完成，找到 " + sources.size() + " 个片段；正在判断是否覆盖问题。");
                } catch (Exception e) {
                    log.warn("RAG 本地检索失败", e);
                    status(sink, "本地知识库暂不可用，将尝试联网补充。");
                }
                if (sink.isCancelled()) return;
                Assessment assessment = research.assess(request, sources, searches);
                if (clarify(sink, assessment)) return;
                Set<String> visited = new HashSet<>();
                boolean expand = !request.history().isEmpty() && assessment.wantsMore();
                int rounds = 0;
                while (rounds < maxRounds && (sources.isEmpty() || !assessment.sufficient() || expand)) {
                    if (sink.isCancelled()) return;
                    rounds++;
                    status(sink, "第 " + rounds + "/" + maxRounds + " 轮联网补充：" + assessment.missingPoints());
                    String query = assessment.searchQuery();
                    if (query == null || query.isBlank()) query = request.techStack() + " " + request.question() + " official documentation";
                    searches.add(query);
                    try {
                        var candidates = web.search(query).stream()
                                .filter(c -> TechStackDiscoveryService.isPublicWebUrl(c.url()))
                                .filter(c -> !visited.contains(c.url())).toList();
                        var pages = new ArrayList<DocsFetcher.Page>();
                        if (!candidates.isEmpty()) {
                            for (int index : research.selectPages(request, assessment, candidates, maxPages).stream().limit(maxPages).toList()) {
                                if (sink.isCancelled()) return;
                                String url = candidates.get(index).url();
                                if (!visited.add(url)) continue;
                                status(sink, "正在读取第 " + rounds + " 轮资料正文：" + candidates.get(index).title());
                                try {
                                    var page = fetcher.fetchPage(url);
                                    if (TechStackDiscoveryService.isPublicWebUrl(page.url()) && !page.text().isBlank()) {
                                        visited.add(page.url());
                                        // 只向模型传入正文的有限窗口，不保存整页。
                                        pages.add(new DocsFetcher.Page(page.title(), page.url(),
                                                page.text().substring(0, Math.min(24000, page.text().length()))));
                                    }
                                } catch (Exception e) { log.warn("RAG 正文抓取失败: {}", url); }
                            }
                        }
                        if (!pages.isEmpty()) addSources(sources, research.extract(request, pages));
                        else status(sink, "本轮未获得可核对的正文，继续依据已有资料判断。");
                    } catch (Exception e) {
                        log.warn("RAG 联网补充失败，轮次={}", rounds, e);
                        status(sink, "本轮联网补充未成功，已取得的资料仍会保留。");
                    }
                    if (sink.isCancelled()) return;
                    assessment = research.assess(request, sources, searches);
                    if (clarify(sink, assessment)) return;
                    expand = false;
                }
                if (sink.isCancelled()) return;
                boolean sufficient = !sources.isEmpty() && assessment.sufficient();
                sink.next(new RagEvent("scope", assessment.scope()));
                status(sink, sufficient ? "资料已覆盖当前问题，正在生成带引用的回答…"
                        : "资料仍有缺口，将回答有依据的部分并说明未确认内容。");
                String context = research.toJson(Map.of("request", request, "assessment", assessment, "evidence", sources));
                StringBuilder answer = new StringBuilder();
                var generation = guide.answerStream(context).subscribe(token -> {
                    answer.append(token);
                    sink.next(new RagEvent("answer", token));
                }, error -> fail(sink, error), () -> {
                    if (sink.isCancelled()) return;
                    if (answer.isEmpty()) {
                        fail(sink, new IllegalStateException("未生成回答"));
                        return;
                    }
                    List<RagSource> cited = citedSources(answer.toString(), sources);
                    sink.next(new RagEvent("sources", cited));
                    sink.next(new RagEvent("done", Map.of("sufficient", sufficient)));
                    sink.complete();
                    List<RagSource> webCited = cited.stream().filter(s -> s.retrievedFrom().equals("web")).toList();
                    if (!webCited.isEmpty()) Schedulers.boundedElastic().schedule(() -> cache(request, webCited));
                });
                sink.onCancel(generation);
            } catch (Exception e) { fail(sink, e); }
        // SSE 写线程会逐条请求事件；不要把请求也排到正在阻塞抓取/推理的同一工作线程。
        }).subscribeOn(Schedulers.boundedElastic(), false);
    }

    private void cache(LearnRequest request, List<RagSource> cited) {
        try {
            int added = knowledge.save(research.selectForStorage(request, cited));
            log.info("RAG 精选回写完成，新增片段={}", added);
        } catch (Exception e) { log.warn("RAG 异步回写失败，已完成的回答不受影响", e); }
    }

    static List<RagSource> citedSources(String answer, List<RagSource> sources) {
        Set<String> ids = new HashSet<>();
        CITATION.matcher(answer).results().forEach(m -> ids.add(m.group(1)));
        return sources.stream().filter(s -> ids.contains(s.id())).toList();
    }

    private void addSources(List<RagSource> sources, List<RagSource> added) {
        for (RagSource source : added) {
            if (sources.stream().noneMatch(s -> s.excerpt().equals(source.excerpt())))
                sources.add(source.withId("S" + (sources.size() + 1)));
        }
    }

    private boolean clarify(FluxSink<RagEvent> sink, Assessment assessment) {
        if (assessment.clarification() == null || assessment.clarification().isBlank()) return false;
        sink.next(new RagEvent("scope", assessment.scope()));
        sink.next(new RagEvent("clarification", assessment.clarification()));
        sink.next(new RagEvent("done", Map.of("clarification", true)));
        sink.complete();
        return true;
    }

    private void status(FluxSink<RagEvent> sink, String message) { sink.next(new RagEvent("status", message)); }

    private void fail(FluxSink<RagEvent> sink, Throwable error) {
        log.warn("RAG 问答中断", error);
        sink.next(new RagEvent("error", "本轮回答未完成，请重试；已有回答已保留。"));
        sink.complete();
    }
}
