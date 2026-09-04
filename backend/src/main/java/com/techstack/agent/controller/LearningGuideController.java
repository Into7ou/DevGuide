package com.techstack.agent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.techstack.agent.dto.LearnRequest;
import com.techstack.agent.dto.LearningGuideResponse;
import com.techstack.agent.service.LearningAgentService;

/**
 * 学习引导 Agent 接口。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class LearningGuideController {

    private final LearningAgentService learningAgentService;

    public LearningGuideController(LearningAgentService learningAgentService) {
        this.learningAgentService = learningAgentService;
    }

    @PostMapping("/learn")
    public LearningGuideResponse learn(@RequestBody LearnRequest request) {
        return learningAgentService.learn(request.techStack(), request.question());
    }

    @PostMapping("/{techStack}/ingest")
    public Map<String, Integer> ingest(@PathVariable String techStack) {
        return Map.of("chunks", learningAgentService.ingest(techStack));
    }
}
