package com.techstack.agent.dto;

/** SSE data 的统一结构：status / scope / answer / sources / clarification / done / error。 */
public record RagEvent(String type, Object data) { }
