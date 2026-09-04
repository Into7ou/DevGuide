package com.techstack.agent.tool;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地知识库检索工具：查询已入库的官方文档 + README 向量片段。
 */
@Slf4j
@Component
public class KnowledgeSearchTools {

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchTools(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "在本地已入库的技术栈知识库（官方文档 + README）中做语义检索，返回最相关的文本片段及其来源 URL")
    public String searchKnowledge(
            @ToolParam(description = "检索问题，如 \"React Hooks 怎么入门\"") String query,
            @ToolParam(description = "可选，限定技术栈名（与入库时的 tech_stack 元数据匹配）") String techStack) {
        if (query == null || query.isBlank()) {
            return "检索问题不能为空";
        }
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query.trim())
                .topK(5)
                .similarityThreshold(0.2);
        if (techStack != null && !techStack.isBlank()) {
            Filter.Expression filter = new FilterExpressionBuilder().eq("tech_stack", techStack.trim()).build();
            builder.filterExpression(filter);
        }
        try {
            List<Document> docs = vectorStore.similaritySearch(builder.build());
            if (docs.isEmpty()) {
                return "本地知识库未检索到相关内容" + (techStack == null ? "" : "（技术栈：" + techStack + "）");
            }
            List<KnowledgeHit> hits = docs.stream()
                    .map(d -> new KnowledgeHit(
                            d.getMetadata().get("source") == null ? null : d.getMetadata().get("source").toString(),
                            d.getText()))
                    .toList();
            return toJson(hits);
        } catch (Exception e) {
            log.warn("知识库检索失败，errorType={}", e.getClass().getSimpleName());
            return "本地知识库检索失败，请基于其他资料继续作答";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("工具结果序列化失败，errorType={}", e.getClass().getSimpleName());
            return "[]";
        }
    }

    record KnowledgeHit(String source, String text) {
    }
}
