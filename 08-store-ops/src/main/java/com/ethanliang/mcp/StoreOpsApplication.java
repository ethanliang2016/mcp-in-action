package com.ethanliang.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 8 篇 · 门店运营助手 MCP Server（无状态）。
 *
 * 端点默认为 /mcp，端口 8088，见 application.yml。
 * 示例业务域：门店销售 / 库存 / 设备 / 补货，全 mock 数据。
 */
@SpringBootApplication
public class StoreOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreOpsApplication.class, args);
    }
}
