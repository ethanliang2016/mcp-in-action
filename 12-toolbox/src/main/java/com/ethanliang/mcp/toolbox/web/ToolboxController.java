package com.ethanliang.mcp.toolbox.web;

import com.ethanliang.mcp.toolbox.model.ToolboxDefinition;
import com.ethanliang.mcp.toolbox.model.ToolboxStatus;
import com.ethanliang.mcp.toolbox.registry.ToolboxRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 最小可观测输出（S级#12）：GET /api/toolboxes 返回所有 Toolbox 的
 * name/version/status/owner/成功率，供治理看板/巡检使用。
 *
 * 管理员视角（scope=*，tenant=null），列出全部 ACTIVE/DEPRECATED。
 */
@RestController
@RequestMapping("/api/toolboxes")
public class ToolboxController {

    private final ToolboxRegistry registry;

    public ToolboxController(ToolboxRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ToolboxView> list() {
        return registry.list("admin", List.of("*"), null).stream()
                .map(d -> new ToolboxView(
                        d.getId().name(),
                        d.getVersion(),
                        d.getStatus(),
                        d.getOwnerTeamId(),
                        round(d.getSuccessRate())))
                .toList();
    }

    /** 治理看板视图（与正文贴的真实 JSON 对应）。 */
    public record ToolboxView(String name, String version, ToolboxStatus status, String ownerTeamId, double successRate) {
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
