package com.ethanliang.mcp.apps.ui;

import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 给「仪表盘工具」挂 _meta。
 *
 * MCP Apps（SEP-1865）的绑定写在**工具自身的元数据**里，不在每次调用结果里：
 *
 * <pre>
 * _meta.ui.resourceUri = ui://room-dashboard   // 指向界面资源
 * _meta.ui.visibility  = ["model"]             // 模型可见
 * </pre>
 *
 * 为什么不能直接写在 @Tool 上：Spring AI 的 ToolDefinition
 * 只有 name / description / inputSchema 三个方法，**没有 meta**，
 * 所以 @Tool 这条路挂不上 _meta。@McpTool 提供了 metaProvider 属性，是官方入口。
 */
@Component
public class DashboardUiMetaProvider implements MetaProvider {

    /** 界面资源 URI，工具与资源靠它对上 */
    public static final String DASHBOARD_URI = "ui://room-dashboard";

    @Override
    public Map<String, Object> getMeta() {
        return Map.of("ui", Map.of(
                "resourceUri", DASHBOARD_URI,
                "visibility", List.of("model")));
    }
}
