package com.ethanliang.mcp.multitenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 第 13 篇 · MCP 多租户隔离。
 *
 * 一个网关服务多个团队，把"多租户"这层补在已有网关上，四件套：
 *   1. 租户路由：请求进来先认出是哪个租户（X-Api-Key / X-Tenant-Id）
 *   2. 凭证隔离：每租户独立 Key，Key→tenant 映射存 Redis，删键即吊销
 *   3. 配额管理：Redis + Lua 固定窗口计数，跨副本一致，超限 429
 *   4. 目录/数据隔离：tools/list 按租户过滤（看不见就点不到）+ 日志 MDC 带 tenant
 *
 * 实测拓扑：两个无状态副本（gw-1/gw-2）+ 共享 Redis，见 docker-compose.yml。
 */
@SpringBootApplication
@EnableConfigurationProperties(TenantProperties.class)
public class MultitenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultitenantApplication.class, args);
    }
}
