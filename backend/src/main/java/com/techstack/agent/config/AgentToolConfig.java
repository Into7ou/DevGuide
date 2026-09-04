package com.techstack.agent.config;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.techstack.agent.tool.GithubTools;
import com.techstack.agent.tool.KnowledgeSearchTools;
import com.techstack.agent.tool.WebSearchTools;

/**
 * 把 @Tool 注解的工具类统一收集成 ToolCallback 数组，供 Agent 注册进 ChatClient。
 */
@Configuration
public class AgentToolConfig {

    @Bean
    public ToolCallback[] agentToolCallbacks(GithubTools githubTools,
                                             KnowledgeSearchTools knowledgeSearchTools,
                                             WebSearchTools webSearchTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(githubTools, knowledgeSearchTools, webSearchTools)
                .build()
                .getToolCallbacks();
    }
}
