package com.techstack.agent.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M1 骨架验证接口：验证 DeepSeek 对话 + 通义 embedding 连通性。
 * 后续阶段会替换为真正的业务接口（技术栈查询 / GitHub Top10 / 学习引导）。
 */
@RestController
@RequestMapping("/api")
public class AiVerifyController {

    private final ChatClient deepSeekClient;
    private final EmbeddingModel embeddingModel;

    public AiVerifyController(DeepSeekChatModel deepSeekChatModel, EmbeddingModel embeddingModel) {
        this.deepSeekClient = ChatClient.builder(deepSeekChatModel).build();
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/chat")
    public Map<String, String> chat(@RequestParam(defaultValue = "你好，用一句话介绍你自己") String prompt) {
        String reply = deepSeekClient.prompt().user(prompt).call().content();
        return Map.of("reply", reply == null ? "" : reply);
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam(defaultValue = "Spring Boot") String text) {
        float[] vector = embeddingModel.embed(text);
        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("dimensions", vector.length);
        result.put("embeddingModel", embeddingModel.getClass().getSimpleName());
        return result;
    }
}
