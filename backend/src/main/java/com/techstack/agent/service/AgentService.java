package com.techstack.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.techstack.agent.security.ReactorSecurityContext;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * M6 ReAct Agent：把「搜 GitHub / 抓文档 / 联网搜索 / 检索知识库」注册为工具，
 * 由 DeepSeek 自主决定调用顺序（ReAct 循环由 ChatClient + ToolCallingManager 内部完成）。
 */
@Slf4j
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            你是一名技术栈学习助手（ReAct Agent）。用户会给出一个技术栈名或学习问题。
            你可以调用以下工具自主完成任务：
            - searchGithubRepos：搜 GitHub 上该技术栈 star 最高的开源项目
            - fetchRepoReadme：抓取某个仓库的 README 原文
            - webSearch：联网搜索官方文档、入门教程、最新动态
            - searchKnowledge：在本地已入库的知识库（官方文档+README）中语义检索
            请自主规划并组合调用这些工具（例如先 webSearch 找官方文档、再 searchGithubRepos 找项目、
            必要时 fetchRepoReadme 深入了解），最后基于收集到的资料，生成清晰的学习路线和核心概念讲解。
            要求：只依据工具返回的资料作答，不要编造；引用来源时注明 URL；若某工具返回「暂不可用」，
            就基于其他可用工具的结果作答，明确告知用户哪些资料暂时缺失。
            """;

    private final DeepSeekChatModel chatModel;
    private final ToolCallback[] localToolCallbacks;
    private final ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbacksProvider;

    public AgentService(DeepSeekChatModel chatModel,
                        ToolCallback[] agentToolCallbacks,
                        ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbacksProvider) {
        this.chatModel = chatModel;
        this.localToolCallbacks = agentToolCallbacks;
        this.mcpToolCallbacksProvider = mcpToolCallbacksProvider;
    }

    /**
     * 同步生成学习引导（Agent 自主编排，最终返回完整回答）。
     */
    public String run(String query) {
        return buildClient().prompt()
                .system(SYSTEM_PROMPT)
                .user(query)
                .call()
                .content();
    }

    /**
     * 流式生成学习引导（SSE 用），逐 token 返回。
     */
    public Flux<String> stream(String query) {
        Flux<String> pipeline = Flux.defer(() -> buildClient().prompt()
                .system(SYSTEM_PROMPT)
                .user(query)
                .stream()
                .content());
        return ReactorSecurityContext.onBlockingScheduler(pipeline);
    }

    /**
     * 每次调用时构建 ChatClient：本地工具 + （可选）MCP 工具。
     * MCP 工具懒加载：首次 Agent 调用时 Web 容器已就绪，自连 SSE 才能成功；失败则降级为仅本地工具。
     */
    private ChatClient buildClient() {
        ChatClient.Builder builder = ChatClient.builder(chatModel).defaultToolCallbacks(localToolCallbacks);
        try {
            SyncMcpToolCallbackProvider mcp = mcpToolCallbacksProvider.getIfAvailable();
            if (mcp != null) {
                ToolCallback[] mcpCallbacks = mcp.getToolCallbacks();
                if (mcpCallbacks.length > 0) {
                    builder.defaultToolCallbacks(mcpCallbacks);
                }
            }
        } catch (Exception e) {
            log.warn("MCP 工具加载失败，降级为仅本地工具，errorType={}", e.getClass().getSimpleName());
        }
        return builder.build();
    }
}
