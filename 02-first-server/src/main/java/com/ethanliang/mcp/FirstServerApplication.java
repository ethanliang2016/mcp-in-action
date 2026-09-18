package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 2 篇 · 第一个 MCP Server（SSE + 有状态）
 *
 * 启动后 MCP 端点见 application.yml 的 sse-message-endpoint。
 */
@SpringBootApplication
public class FirstServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FirstServerApplication.class, args);
    }
}
