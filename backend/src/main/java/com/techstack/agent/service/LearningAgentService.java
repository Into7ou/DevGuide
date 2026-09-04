package com.techstack.agent.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.GithubRepoDto;
import com.techstack.agent.dto.LearningGuideResponse;
import com.techstack.agent.entity.TechStack;
import com.techstack.agent.github.GithubClient;
import com.techstack.agent.mapper.TechStackMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 学习引导 Agent：RAG 入库（文档+README → 切分 → embedding → 向量库）+ 检索 + DeepSeek 生成。
 */
@Slf4j
@Service
public class LearningAgentService {

    private static final String SYSTEM_PROMPT =
            "你是一名技术栈学习助手。请基于给定的参考资料回答用户问题，给出清晰的学习路线和核心概念讲解，"
                    + "只依据资料内容作答，不要编造资料中没有的信息，并在回答中注明引用的来源 URL。";

    private static final int MAX_QUESTION_LENGTH = 500;

    private final TechStackMapper techStackMapper;
    private final GithubService githubService;
    private final GithubClient githubClient;
    private final DocsFetcher docsFetcher;
    private final VectorStore vectorStore;
    private final DeepSeekChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TokenTextSplitter splitter = new TokenTextSplitter();
    /** 按技术栈名的入库锁，避免并发首次 learn 重复入库/互相清库 */
    private final ConcurrentHashMap<String, ReentrantLock> ingestLocks = new ConcurrentHashMap<>();

    public LearningAgentService(TechStackMapper techStackMapper, GithubService githubService,
                                GithubClient githubClient, DocsFetcher docsFetcher, VectorStore vectorStore,
                                DeepSeekChatModel chatModel, ObjectMapper objectMapper) {
        this.techStackMapper = techStackMapper;
        this.githubService = githubService;
        this.githubClient = githubClient;
        this.docsFetcher = docsFetcher;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成学习引导：首次调用先入库，再检索相关资料，最后用 DeepSeek 生成带引用的回答。
     */
    public LearningGuideResponse learn(String techStackName, String question) {
        TechStack stack = requireStack(techStackName);
        if (stack.getRagIngestedAt() == null) {
            ingestLocked(stack.getName());
        }

        String q = normalizeQuestion(question, stack.getName());

        // similarityThreshold 0.2 为宽松下限，避免误过滤相关片段（可按真实数据进一步校准）
        List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(q)
                        .topK(4)
                        .similarityThreshold(0.2)
                        .filterExpression(filter("tech_stack", stack.getName()))
                        .build());

        if (retrieved.isEmpty()) {
            return new LearningGuideResponse("未检索到相关资料，请先确认该技术栈已成功入库。", List.of());
        }

        String answer = generateAnswer(stack.getName(), q, retrieved);
        return new LearningGuideResponse(answer, extractCitations(retrieved));
    }

    /**
     * 主动入库，返回入库的片段数（0 表示已入库或本次未抓到任何有效内容）。
     */
    public int ingest(String techStackName) {
        return ingestLocked(requireStack(techStackName).getName());
    }

    private int ingestLocked(String stackName) {
        ReentrantLock lock = ingestLocks.computeIfAbsent(stackName, k -> new ReentrantLock());
        lock.lock();
        try {
            TechStack stack = requireStack(stackName);
            if (stack.getRagIngestedAt() != null) {
                return 0;
            }

            // 先抓取，抓取成功后才清旧数据 + 写入，避免「先删后抓失败」把向量库清空
            List<Document> documents = fetchDocuments(stack);
            List<Document> chunks = splitter.apply(documents);
            if (chunks.isEmpty()) {
                log.error("入库失败：未抓取到任何有效片段");
                return 0;
            }

            vectorStore.delete(filter("tech_stack", stack.getName()));
            vectorStore.add(chunks);

            // 只更新 rag_ingested_at 单列，避免整行回写造成并发丢失更新
            techStackMapper.markIngested(stack.getId(), LocalDateTime.now());
            return chunks.size();
        } finally {
            lock.unlock();
        }
    }

    private List<Document> fetchDocuments(TechStack stack) {
        List<Document> documents = new ArrayList<>();

        for (String url : parseDocKeyPages(stack.getDocKeyPages())) {
            if (url == null || url.isBlank()) {
                continue;
            }
            try {
                String text = docsFetcher.fetchText(url);
                if (text != null && !text.isBlank()) {
                    documents.add(new Document(text,
                            Map.of("tech_stack", stack.getName(), "source", url, "type", "official_doc")));
                }
            } catch (Exception e) {
                log.warn("抓取官方文档失败");
            }
        }

        try {
            for (GithubRepoDto repo : githubService.topRepos(stack.getGithubSearchQuery())) {
                try {
                    String readme = githubClient.fetchReadme(repo.fullName());
                    if (readme != null && !readme.isBlank()) {
                        documents.add(new Document(readme, Map.of(
                                "tech_stack", stack.getName(),
                                "source", repo.htmlUrl(),
                                "type", "github_readme",
                                "repo", repo.fullName())));
                    }
                } catch (Exception e) {
                    log.warn("抓取 README 失败");
                }
            }
        } catch (Exception e) {
            log.warn("拉取 Top10 仓库失败");
        }

        return documents;
    }

    private String generateAnswer(String techStack, String question, List<Document> retrieved) {
        StringBuilder context = new StringBuilder();
        for (Document doc : retrieved) {
            Object source = doc.getMetadata().get("source");
            context.append("来源: ").append(source instanceof String ? source : "未知")
                    .append('\n')
                    .append(doc.getText())
                    .append("\n\n");
        }
        String user = "技术栈：" + techStack + "\n\n参考资料：\n" + context + "\n用户问题：" + question;
        return ChatClient.builder(chatModel).build()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(user)
                .call()
                .content();
    }

    private String normalizeQuestion(String question, String techStack) {
        if (question == null || question.isBlank()) {
            return "请生成" + techStack + "的学习路线和核心概念讲解";
        }
        String trimmed = question.trim();
        return trimmed.length() > MAX_QUESTION_LENGTH ? trimmed.substring(0, MAX_QUESTION_LENGTH) : trimmed;
    }

    private TechStack requireStack(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        TechStack stack = techStackMapper.findByNameIgnoreCase(name);
        if (stack == null) {
            stack = techStackMapper.findByAlias(name);
        }
        if (stack == null) {
            throw new IllegalArgumentException("未知技术栈: " + name + "，请使用预置清单中的技术栈");
        }
        return stack;
    }

    private Filter.Expression filter(String key, String value) {
        return new FilterExpressionBuilder().eq(key, value).build();
    }

    /** 从检索结果中提取去重后的来源 URL（仅接受 String 类型）。 */
    static List<String> extractCitations(List<Document> retrieved) {
        return retrieved.stream()
                .map(d -> d.getMetadata().get("source"))
                .filter(Objects::nonNull)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .distinct()
                .toList();
    }

    private List<String> parseDocKeyPages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("解析 docKeyPages 失败，errorType={}", e.getClass().getSimpleName());
            return List.of();
        }
    }
}
