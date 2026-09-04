package com.techstack.agent.dto;

/**
 * 学习引导请求。
 *
 * @param techStack 当前页面技术栈，作为理解问题的上下文
 * @param question  可选的具体问题，为空时生成通用学习路线
 */
public record LearnRequest(String techStack, String question, java.util.List<Turn> history) {
    public LearnRequest {
        history = history == null ? java.util.List.of()
                : java.util.List.copyOf(history.subList(Math.max(0, history.size() - 3), history.size()));
    }

    public LearnRequest(String techStack, String question) {
        this(techStack, question, java.util.List.of());
    }

    /** 历史仅用来理解追问，不作为已经核验的证据。 */
    public record Turn(String question, String answer, java.util.List<RagSource> sources) { }
}
