package com.ethanliang.mcp.apps.ui;

import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 给「界面专用工具」挂 _meta：visibility = app。
 *
 * 意思是这个工具**只留在宿主侧**，由界面里的按钮触发，
 * 不交给模型——避免模型绕过用户直接操作设备。
 * 渲染权限与业务权限分开，是 MCP Apps 明确要求的一条边界。
 */
@Component
public class AppOnlyUiMetaProvider implements MetaProvider {

    @Override
    public Map<String, Object> getMeta() {
        return Map.of("ui", Map.of("visibility", List.of("app")));
    }
}
