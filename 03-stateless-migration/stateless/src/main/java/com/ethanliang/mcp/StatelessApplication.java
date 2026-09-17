package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 3 篇 · 新版 MCP Server（Spring AI 2.x + STATELESS 无状态）
 *
 * 端点默认为 /mcp，见 application.yml。
 */
@SpringBootApplication
public class StatelessApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatelessApplication.class, args);
    }
}
