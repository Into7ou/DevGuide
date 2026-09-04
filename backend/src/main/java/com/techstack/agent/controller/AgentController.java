package com.techstack.agent.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstack.agent.dto.LearnRequest;
import com.techstack.agent.dto.RagEvent;
import com.techstack.agent.service.AgentService;
import com.techstack.agent.service.MultiAgentService;
import com.techstack.agent.service.GenerationAdmissionPolicy;
import com.techstack.agent.service.GenerationAdmissionPolicy.Permit;
import com.techstack.agent.security.AuthenticatedSubject;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * M6 ReAct Agent 接口：自由输入任意技术栈，Agent 自主编排（搜 GitHub / 抓文档 / 联网 / 检索）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentService agentService;
    private final MultiAgentService multiAgentService;
    private final ObjectMapper objectMapper;
    private final GenerationAdmissionPolicy admissionPolicy;

    public AgentController(AgentService agentService, MultiAgentService multiAgentService, ObjectMapper objectMapper,
                           GenerationAdmissionPolicy admissionPolicy) {
        this.agentService = agentService;
        this.multiAgentService = multiAgentService;
        this.objectMapper = objectMapper;
        this.admissionPolicy = admissionPolicy;
    }

    /**
     * 同步接口（单 Agent ReAct）：返回完整回答。
     */
    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody LearnRequest request, Authentication authentication) {
        try (Permit ignored = admissionPolicy.acquire(AuthenticatedSubject.key(authentication),
                GenerationAdmissionPolicy.Operation.AGENT)) {
            return Map.of("answer", agentService.run(buildPrompt(request)));
        }
    }

    /**
     * SSE 流式接口（单 Agent ReAct）：逐 token 返回，前端实时展示生成过程。
     */
    @PostMapping(value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody LearnRequest request, Authentication authentication) {
        Permit permit = admissionPolicy.acquire(AuthenticatedSubject.key(authentication),
                GenerationAdmissionPolicy.Operation.AGENT);
        try {
            return agentService.stream(buildPrompt(request)).map(this::toSse).doFinally(ignored -> permit.close());
        } catch (RuntimeException e) {
            permit.close();
            throw e;
        }
    }

    /**
     * 多智能体同步接口：ResearchAgent 搜集资料 → GuideAgent 生成引导。
     */
    @PostMapping("/multi")
    public Map<String, String> multi(@RequestBody LearnRequest request, Authentication authentication) {
        try (Permit ignored = admissionPolicy.acquire(AuthenticatedSubject.key(authentication),
                GenerationAdmissionPolicy.Operation.AGENT)) {
            return Map.of("answer", multiAgentService.run(normalize(request)));
        }
    }

    /**
     * 多智能体 SSE 流式接口：先搜集资料，再流式生成引导。
     */
    @PostMapping(value = "/multi/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RagEvent>> multiStream(@RequestBody LearnRequest request,
                                                       Authentication authentication) {
        Permit permit = admissionPolicy.acquire(AuthenticatedSubject.key(authentication),
                GenerationAdmissionPolicy.Operation.AGENT);
        try {
            return multiAgentService.stream(normalize(request))
                    .map(e -> ServerSentEvent.builder(e).build())
                    .doFinally(ignored -> permit.close());
        } catch (RuntimeException e) {
            permit.close();
            throw e;
        }
    }

    private LearnRequest normalize(LearnRequest request) {
        buildPrompt(request);
        String stack = request.techStack() == null ? "" : request.techStack().trim();
        String question = request.question() == null ? "" : request.question().trim();
        if (question.isEmpty()) question = "请生成「" + stack + "」的学习路线和核心概念讲解";
        return new LearnRequest(stack.substring(0, Math.min(stack.length(), MAX_INPUT_LENGTH)),
                question.substring(0, Math.min(question.length(), MAX_INPUT_LENGTH)), request.history());
    }

    /**
     * 把 token 用 JSON 字符串转义后作为 SSE data，避免 token 内换行符破坏 SSE 帧结构。
     * 前端 JSON.parse 后拼接即可还原（含换行）。
     */
    private ServerSentEvent<String> toSse(String token) {
        String escaped;
        try {
            escaped = objectMapper.writeValueAsString(token);
        } catch (JsonProcessingException e) {
            log.warn("SSE token 转义失败，errorType={}", e.getClass().getSimpleName());
            escaped = "\"\"";
        }
        return ServerSentEvent.builder(escaped).build();
    }

    private static final int MAX_INPUT_LENGTH = 500;

    private String buildPrompt(LearnRequest request) {
        String techStack = request.techStack() == null ? "" : request.techStack().trim();
        String question = request.question() == null ? "" : request.question().trim();
        if (techStack.isEmpty() && question.isEmpty()) {
            throw new IllegalArgumentException("techStack 与 question 不能同时为空");
        }
        // 超长输入截断，避免 token 浪费与注入面扩大
        if (techStack.length() > MAX_INPUT_LENGTH) {
            techStack = techStack.substring(0, MAX_INPUT_LENGTH);
        }
        if (question.length() > MAX_INPUT_LENGTH) {
            question = question.substring(0, MAX_INPUT_LENGTH);
        }
        if (question.isEmpty()) {
            question = "请生成「" + techStack + "」的学习路线和核心概念讲解";
        }
        return techStack.isEmpty() ? question : "技术栈：" + techStack + "\n问题：" + question;
    }
}
