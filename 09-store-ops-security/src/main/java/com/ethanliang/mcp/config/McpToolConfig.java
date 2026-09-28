package com.ethanliang.mcp.config;

import com.ethanliang.mcp.service.SecureStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Slf4j
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider secureStoreToolCallbackProvider(SecureStoreService svc) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(svc)
                .build();
        Arrays.stream(provider.getToolCallbacks())
                .forEach(tcb -> log.info("Registered Tool: {}", tcb.getToolDefinition().name()));
        return provider;
    }
}
