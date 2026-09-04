package com.techstack.agent.dto;

import java.util.List;

/** excerpt 始终来自实际抓取正文或知识库原文，id 由本轮服务端分配。 */
public record RagSource(String id, String title, String url, String excerpt,
                        List<String> techStacks, String sourceType, String retrievedFrom) {
    public RagSource withId(String value) {
        return new RagSource(value, title, url, excerpt, techStacks, sourceType, retrievedFrom);
    }
}
