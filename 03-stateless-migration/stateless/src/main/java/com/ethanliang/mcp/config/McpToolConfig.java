package com.ethanliang.mcp.config;

import com.ethanliang.mcp.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 工具注册（与旧版写法一致）。
 *
 * 注意：无状态模式下 ToolContext 不适用，工具之间不要依赖上下文传递状态。
 */
@Slf4j
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider deviceToolCallbackProvider(DeviceService deviceService) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(deviceService)
                .build();

        Arrays.stream(provider.getToolCallbacks())
                .map(tcb -> tcb.getToolDefinition().name())
                .forEach(name -> log.info("Registered Tool: {}", name));

        return provider;
    }
}