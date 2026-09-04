package com.techstack.agent.dto;

/**
 * 技术栈清单项（对外输出）。
 */
public record TechStackDto(
        Long id,
        String name,
        String description,
        String officialDocUrl,
        String category) {
}
