package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 9 篇 · 门店运营助手 MCP Server（6 层安全版）。
 *
 * 在 08-store-ops 基础上加安全层：
 *   L1 传输安全（mTLS）：本模块用配置说明 + 部署侧实现，代码层演示 SSL 配置开关
 *   L2 客户端身份：静态 Bearer Token（对应 CIMD 思路的本地简化演示）
 *   L3 用户身份透传：X-User-Id / _meta 传递，工具层校验（过渡方案，非完整安全边界）
 *   L4 RBAC：店长只能查自己门店
 *   L5 限流：Bucket4j 按 token 桶限流
 *   L6 审计：每次调用打结构化审计日志
 *
 * 端口 8090，端点 /mcp。
 */
@SpringBootApplication
public class StoreSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreSecurityApplication.class, args);
    }
}
