package com.techstack.agent.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.toolcalling.common.interfaces.SearchService;
import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 联网搜索工具：封装 Tavily 搜索，供 Agent 检索互联网上的最新信息（如技术栈官方文档入口、教程）。
 */
@Slf4j
@Component
public class WebSearchTools {

    private final TavilySearchService tavilySearchService;
    private final ObjectMapper objectMapper;

    public WebSearchTools(TavilySearchService tavilySearchService, ObjectMapper objectMapper) {
        this.tavilySearchService = tavilySearchService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "联网搜索互联网上的信息（如某技术栈的官方文档、入门教程、最新动态），返回标题、摘要和链接")
    public String webSearch(@ToolParam(description = "搜索关键词，如 \"React 官方文档\"") String query) {
        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空";
        }
        try {
            SearchService.Response response = tavilySearchService.query(query.trim());
            if (response == null || response.getSearchResult() == null) {
                return "未获取到搜索结果";
            }
            List<SearchService.SearchContent> results = response.getSearchResult().results();
            if (results == null || results.isEmpty()) {
                return "未搜索到与「" + query + "」相关的内容";
            }
            List<SearchHit> hits = results.stream()
                    .map(r -> new SearchHit(r.title(), truncate(r.content(), 300), r.url()))
                    .limit(8)
                    .toList();
            return toJson(hits);
        } catch (Exception e) {
            log.warn("联网搜索失败，errorType={}", e.getClass().getSimpleName());
            return "联网搜索服务暂不可用，请基于已获取的 GitHub/知识库资料继续作答";
        }
    }

    /** 截断摘要，避免 8 条结果全文撑爆上下文。 */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("工具结果序列化失败，errorType={}", e.getClass().getSimpleName());
            return "[]";
        }
    }

    /** 一次调用只发出一次搜索；摘要仅用于挑选页面，不作为原文证据。 */
    public List<SearchHit> search(String query) {
        SearchService.Response response = tavilySearchService.query(query);
        if (response == null || response.getSearchResult() == null
                || response.getSearchResult().results() == null) return List.of();
        return response.getSearchResult().results().stream()
                .map(r -> new SearchHit(r.title(), truncate(r.content(), 300), r.url()))
                .limit(8).toList();
    }

    public record SearchHit(String title, String content, String url) {
    }
}
