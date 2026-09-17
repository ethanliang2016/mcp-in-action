package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 3 篇 · 旧版 MCP Server（SSE + 有状态）
 *
 * 启动后 MCP 端点见 application.yml 的 sse-message-endpoint。
 */
@SpringBootApplication
public class LegacySseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacySseApplication.class, args);
    }
}
