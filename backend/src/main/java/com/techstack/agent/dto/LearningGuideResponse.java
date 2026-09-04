package com.techstack.agent.dto;

import java.util.List;

/**
 * 学习引导响应。
 *
 * @param answer    生成的回答
 * @param citations 引用的来源 URL（来自检索到的文档片段）
 */
public record LearningGuideResponse(String answer, List<String> citations) {
}
