package com.ethanliang.mcp.config;

import com.ethanliang.mcp.service.StoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 工具注册。
 *
 * 注意：无状态模式下 ToolContext 不适用，工具之间不要依赖上下文传递状态
 * —— 这正是第 8 篇问题 2 要把状态显式外置的原因。
 */
@Slf4j
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider storeToolCallbackProvider(StoreService storeService) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(storeService)
                .build();

        Arrays.stream(provider.getToolCallbacks())
                .forEach(tcb -> log.info("Registered Tool: {}", tcb.getToolDefinition().name()));

        return provider;
    }
}
