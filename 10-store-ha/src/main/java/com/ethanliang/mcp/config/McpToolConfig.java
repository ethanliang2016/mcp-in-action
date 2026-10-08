package com.ethanliang.mcp.config;

import com.ethanliang.mcp.service.StoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 工具注册（与篇八一致）。
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
