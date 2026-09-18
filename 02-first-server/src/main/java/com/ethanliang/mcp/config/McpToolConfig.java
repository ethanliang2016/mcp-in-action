package com.ethanliang.mcp.config;

import com.ethanliang.mcp.service.RoomMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 工具注册。
 * 把 @Tool 标注的 Spring Bean 注册为 MCP 工具。
 */
@Slf4j
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider roomMonitorToolCallbackProvider(RoomMonitorService roomMonitorService) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(roomMonitorService)
                .build();

        // 启动时打印已注册工具，方便排查
        Arrays.stream(provider.getToolCallbacks())
                .map(ToolCallback::getName)
                .forEach(name -> log.info("Registered Tool: {}", name));

        return provider;
    }
}
