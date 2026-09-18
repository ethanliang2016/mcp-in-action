package com.ethanliang.mcp.apps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 5 篇 · MCP Apps 示例（Spring AI 2.x + STATELESS 无状态）。
 *
 * 端点默认 /mcp，见 application.yml。
 */
@SpringBootApplication
public class McpAppsApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpAppsApplication.class, args);
    }
}
