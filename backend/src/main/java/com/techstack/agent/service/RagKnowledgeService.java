package com.techstack.agent.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.techstack.agent.dto.RagSource;

@Service
public class RagKnowledgeService {
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;

    public RagKnowledgeService(VectorStore vectorStore, JdbcTemplate jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    /** 页面技术栈作为查询上下文；不使用精确技术栈过滤，允许跨栈证据。 */
    public List<RagSource> search(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query)
                        .topK(5).similarityThreshold(0.2).build()).stream()
                .filter(d -> d.getText() != null && !d.getText().isBlank())
                .filter(d -> TechStackDiscoveryService.isPublicWebUrl((String) d.getMetadata().get("source")))
                .map(d -> {
                    Map<String, Object> m = d.getMetadata();
                    List<String> stacks = m.get("tech_stacks") instanceof List<?> list
                            ? list.stream().map(Object::toString).toList()
                            : List.of(m.getOrDefault("tech_stack", "未标注").toString());
                    String type = m.getOrDefault("source_type",
                            "official_doc".equals(m.get("type")) ? "official" : "unknown").toString();
                    return new RagSource("", m.getOrDefault("title", m.get("source")).toString(),
                            m.get("source").toString(), d.getText(), stacks, type, "local");
                }).toList();
    }

    /** 原文稳定 ID 避免并发重复新增；旧版随机 ID 片段也按原文复用。 */
    public int save(List<RagSource> selected) {
        int added = 0;
        for (RagSource source : selected.stream().distinct().limit(3).toList()) {
            if (!"web".equals(source.retrievedFrom())) continue;
            UUID id = UUID.nameUUIDFromBytes(source.excerpt().getBytes(StandardCharsets.UTF_8));
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM vector_store WHERE id = ? OR strpos(content, ?) > 0)",
                    Boolean.class, id, source.excerpt()))) continue;
            vectorStore.add(List.of(Document.builder().id(id.toString()).text(source.excerpt())
                    .metadata(Map.of("source", source.url(), "title", source.title(),
                            "tech_stack", source.techStacks().getFirst(), "tech_stacks", source.techStacks(),
                            "source_type", source.sourceType(), "type", "rag_excerpt")).build()));
            added++;
        }
        return added;
    }
}
