package com.ethanliang.mcp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 第 6 篇 · 自建 MCP 网关。
 *
 * 对外是一个无状态 MCP Server（/mcp，端口 8086），
 * 对内用 MCP client 连多个下游 Server，把它们的工具聚合成一张表。
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class McpGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }
}
