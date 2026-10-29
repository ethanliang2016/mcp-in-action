package com.ethanliang.mcp.multitenant;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具目录与调用端点（租户已由 TenantFilter 解析并写入上下文）。
 *
 * 目录过滤是关键：引子里"财务工具被供应链误调"的根因就是目录没隔开——
 * 租户 list 工具时只返回 allowed_tools 内的工具，看不见就点不到。
 */
@RestController
public class ToolController {

    private final TenantStore store;
    private final TenantProperties properties;

    public ToolController(TenantStore store, TenantProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /** 目录隔离：只返回本租户被授权的工具 */
    @GetMapping("/tools/list")
    public Map<String, Object> list() {
        TenantProperties.TenantDef tenant = store.byId(TenantContext.get());
        List<String> visible = properties.getAllTools().stream()
                .filter(tenant.getAllowedTools()::contains)
                .toList();
        return Map.of("tenant", tenant.getId(), "tools", visible);
    }

    @GetMapping("/mcp")
    public Map<String, Object> mcpInfo() {
        return Map.of("tenant", TenantContext.get(), "status", "ok");
    }

    /** 调用：不在本租户 allowed_tools 内 → 403（凭证/权限隔离） */
    @PostMapping("/mcp")
    public ResponseEntity<?> call(@RequestBody Map<String, String> body) {
        TenantProperties.TenantDef tenant = store.byId(TenantContext.get());
        String tool = body == null ? null : body.get("tool");
        if (tool == null || !tenant.getAllowedTools().contains(tool)) {
            return ResponseEntity.status(403)
                    .body(Map.of("tenant", tenant.getId(), "error", "tool not allowed for this tenant", "tool", tool));
        }
        return ResponseEntity.ok(Map.of("tenant", tenant.getId(), "tool", tool, "result", "ok"));
    }
}
