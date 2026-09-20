package com.ethanliang.mcp.gateway;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 下游后端注册表：建连、拉工具、做命名空间隔离、解析路由。
 *
 * 对外暴露的工具名 = {@code <prefix>__<原工具名>}。
 * 同名工具靠 prefix 隔开——两个机房跑同一套服务时，这是唯一的区分手段。
 *
 * 第 7 篇新增：若后端配置了 authHeader，建连时通过 SDK 的 request customizer
 * 往每个下游请求注入 {@code Authorization: Bearer <authHeader>}，凭证来自环境变量。
 */
@Component
public class BackendRegistry {

    private static final Logger log = LoggerFactory.getLogger(BackendRegistry.class);

    public static final String SEPARATOR = "__";

    private final GatewayProperties props;

    /** prefix -> 后端 */
    private final Map<String, Backend> byPrefix = new LinkedHashMap<>();

    /** 启动时就没连上的后端 */
    private final List<String> offline = new ArrayList<>();

    public BackendRegistry(GatewayProperties props) {
        this.props = props;
    }

    public record Backend(String name, String url, String prefix, McpSyncClient client, List<McpSchema.Tool> tools) {
    }

    public record Route(Backend backend, String originalName) {
    }

    @PostConstruct
    public void connect() {
        for (GatewayProperties.BackendConfig cfg : props.getBackends()) {
            String prefix = cfg.getPrefix();
            try {
                var builder = HttpClientStreamableHttpTransport.builder(cfg.getUrl());
                // 第 7 篇：下游凭证注入。SDK 2.0.0 用 httpRequestCustomizer 改请求头，
                // 没有 customHeaders 这个便捷方法。
                if (cfg.getAuthHeader() != null && !cfg.getAuthHeader().isEmpty()) {
                    // SDK 2.0.0 的 McpSyncHttpClientRequestCustomizer.customize 是 5 参 SAM：
                    // (HttpRequest.Builder, method, uri, body, McpTransportContext)
                    builder.httpRequestCustomizer((requestBuilder, method, uri, body, context) ->
                        requestBuilder.header("Authorization", "Bearer " + cfg.getAuthHeader()));
                    log.info("后端 {} 启用下游鉴权头（凭证来自环境变量）", cfg.getName());
                }
                McpSyncClient client = McpClient.sync(builder.build())
                        .requestTimeout(Duration.ofSeconds(8))
                        .build();
                client.initialize();
                List<McpSchema.Tool> tools = client.listTools().tools();
                byPrefix.put(prefix, new Backend(cfg.getName(), cfg.getUrl(), prefix, client, tools));
                log.info("挂载后端 {}（{}）成功，拉到 {} 个工具", cfg.getName(), cfg.getUrl(), tools.size());
            } catch (Exception e) {
                offline.add(cfg.getName());
                log.warn("后端 {}（{}）挂载失败，网关继续启动，该后端工具不暴露：{}",
                        cfg.getName(), cfg.getUrl(), e.toString());
            }
        }
        log.info("网关就绪：在线后端 {} 个，离线 {} 个，聚合工具 {} 个",
                byPrefix.size(), offline.size(), aggregatedTools().size());
    }

    /** 把各后端的工具加上前缀拼成一张表。 */
    public List<McpSchema.Tool> aggregatedTools() {
        List<McpSchema.Tool> all = new ArrayList<>();
        for (Backend backend : byPrefix.values()) {
            for (McpSchema.Tool tool : backend.tools()) {
                all.add(McpSchema.Tool.builder()
                        .name(backend.prefix() + SEPARATOR + tool.name())
                        .title(tool.title())
                        .description("[" + backend.name() + "] " + tool.description())
                        .inputSchema(tool.inputSchema())
                        .outputSchema(tool.outputSchema())
                        .annotations(tool.annotations())
                        .meta(tool.meta())
                        .build());
            }
        }
        return all;
    }

    /** 网关工具名 -> 后端 + 原始工具名。解析不了返回 null。 */
    public Route resolve(String gatewayToolName) {
        int idx = gatewayToolName == null ? -1 : gatewayToolName.indexOf(SEPARATOR);
        if (idx <= 0) {
            return null;
        }
        Backend backend = byPrefix.get(gatewayToolName.substring(0, idx));
        if (backend == null) {
            return null;
        }
        return new Route(backend, gatewayToolName.substring(idx + SEPARATOR.length()));
    }

    public List<Backend> onlineBackends() {
        return List.copyOf(byPrefix.values());
    }

    public List<String> offlineBackends() {
        return List.copyOf(offline);
    }
}
