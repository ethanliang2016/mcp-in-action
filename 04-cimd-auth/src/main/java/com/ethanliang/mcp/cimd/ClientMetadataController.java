package com.ethanliang.mcp.cimd;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ClientMetadataController {
    // demo 客户端把自己的元数据挂到这个 URL；它的 client_id 就是本端点地址。
    // 本地 demo 用 http://localhost:8084 才能跑通；生产换成你自己的 https 域名。
    @GetMapping("/.well-known/mcp-client")
    public ClientMetadata metadata() {
        return new ClientMetadata(
                "http://localhost:8084/.well-known/mcp-client",
                "机房监控助手",
                List.of("http://localhost:8084/callback"),
                "none",
                "mcp:read");
    }
}
