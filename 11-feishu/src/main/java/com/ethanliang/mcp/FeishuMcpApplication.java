package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 11 篇 · 飞书深度接入示例。
 *
 * 同时验证两件事：
 *  - P3：飞书 SDK (com.larksuite.oapi:oapi-sdk:2.3.4) 与 Spring AI 2.0.1 在
 *        Spring Boot 4.0 + Java 21 下无依赖冲突（启动即验证 classpath 共存）。
 *  - P5：门店运营助手工具（篇8 业务域，@Tool 引用，零重写）被本模块 MCP Server 挂载。
 *
 * 飞书长连接在 FeishuLongConnConfig 中以守护线程启动；群内 @ 机器人即触发回复。
 */
@SpringBootApplication
public class FeishuMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeishuMcpApplication.class, args);
    }
}
