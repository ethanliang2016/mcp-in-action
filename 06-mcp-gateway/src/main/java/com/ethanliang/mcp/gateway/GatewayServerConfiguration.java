package com.ethanliang.mcp.gateway;

import java.util.List;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关对上游的那一面：一个无状态 MCP Server。
 *
 * tools/list 返回聚合后的工具表，tools/call 按前缀转发给对应后端。
 * 后端不可用不是网关的错误——返回 isError 的工具结果，其他后端的工具照常可用。
 */
@Configuration
public class GatewayServerConfiguration {

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpGatewayServlet(BackendRegistry registry) {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .messageEndpoint("/mcp")
                .build();

        List<McpSchema.Tool> tools = registry.aggregatedTools();
        List<McpStatelessServerFeatures.SyncToolSpecification> specs = tools.stream()
                .map(tool -> McpStatelessServerFeatures.SyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler((context, request) -> callDownstream(registry, request))
                        .build())
                .toList();

        McpServer.sync(transport)
                .serverInfo("mcp-gateway", "1.0.0")
                .tools(specs)
                .build();

        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport, "/mcp");
        registration.setName("mcp-gateway");
        return registration;
    }

    private McpSchema.CallToolResult callDownstream(BackendRegistry registry, McpSchema.CallToolRequest request) {
        BackendRegistry.Route route = registry.resolve(request.name());
        if (route == null) {
            return error("网关不认识这个工具：" + request.name());
        }
        try {
            return route.backend().client()
                    .callTool(new McpSchema.CallToolRequest(route.originalName(), request.arguments()));
        } catch (Exception e) {
            // 单个后端故障不下传成网关级失败：让它带着错误信息回给模型
            return error("后端 " + route.backend().name() + " 调用失败：" + e.getMessage());
        }
    }

    private McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }
}
