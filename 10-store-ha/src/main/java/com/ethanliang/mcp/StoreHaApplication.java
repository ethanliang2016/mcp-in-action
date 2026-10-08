package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 10 篇 · 门店运营助手 MCP Server（高可用版）。
 *
 * 在篇八无状态的基础上叠加：
 *   - 多实例水平扩容（Nginx 加权轮询 / K8s Service）
 *   - Actuator 健康检查（/actuator/health，供 LB 摘除）
 *   - 查设备状态接 Resilience4j 熔断（慢调用也计入）
 *   - 查门店销售接 Redis 缓存，做软降级
 *   - 上下文与规则表外置 Redis（哨兵模式）
 *
 * 端口 8088，端点 /mcp，见 application.yml。
 */
@SpringBootApplication
public class StoreHaApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreHaApplication.class, args);
    }
}
