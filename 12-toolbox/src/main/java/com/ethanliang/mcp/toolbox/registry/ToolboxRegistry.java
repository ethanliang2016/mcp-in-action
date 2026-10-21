package com.ethanliang.mcp.toolbox.registry;

import com.ethanliang.mcp.toolbox.model.ToolboxDefinition;
import com.ethanliang.mcp.toolbox.model.ToolboxId;

import java.time.Clock;
import java.util.List;
import java.util.TreeMap;

/**
 * Toolbox 注册表抽象（治理层核心契约）。
 * 两种实现：InMemoryToolboxRegistry（本机 demo，默认）、RedisToolboxRegistry（多实例一致，profile=redis）。
 */
public interface ToolboxRegistry {

    void register(ToolboxDefinition def);

    List<ToolboxDefinition> list(String callerId, List<String> callerScopes, String tenantId);

    ToolboxDefinition resolve(String callerId, List<String> callerScopes, ToolboxId id, String requestedVersion);

    void setGrayRoute(ToolboxId id, String callerId, String version);

    void deprecate(ToolboxId id);

    void advanceLifecycle();

    TreeMap<String, ToolboxDefinition> versions(ToolboxId id);

    void setClock(Clock clock);
}
