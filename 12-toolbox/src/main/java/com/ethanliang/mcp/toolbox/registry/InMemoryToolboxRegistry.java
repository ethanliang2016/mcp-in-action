package com.ethanliang.mcp.toolbox.registry;

import com.ethanliang.mcp.toolbox.model.ToolboxDefinition;
import com.ethanliang.mcp.toolbox.model.ToolboxId;
import com.ethanliang.mcp.toolbox.model.ToolboxStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量 Toolbox 注册表（内存后端，治理层核心，默认实现）。
 *
 * 存储结构：Map<ToolboxId, TreeMap<SemVer, ToolboxDefinition>>
 *   —— 同一 Toolbox 可并存多版本（v1/v2 同时注册），按 caller 路由到对应版本。
 *
 * 权限两层拦截：
 *   ① 发现层 list()：按 caller 的 scope + tenant 过滤可见工具；
 *   ② 调用层 resolve()：实际调用前校验 caller 是否持有 requiredScope。
 *
 * 弃用状态机：deprecate() 标期 -> 宽限 >=14天 advanceLifecycle() -> REMOVED（stub 保留30天由清理任务决定）。
 *
 * 说明：演示用字符串版 SemVer 比较（reverseOrder）。生产应解析 SemVer 数值比较。
 */
@Component
@Profile("!redis")
public class InMemoryToolboxRegistry implements ToolboxRegistry {

    private final Map<ToolboxId, TreeMap<String, ToolboxDefinition>> store = new ConcurrentHashMap<>();
    private final Map<ToolboxId, Map<String, String>> grayRoutes = new ConcurrentHashMap<>(); // id -> (callerId -> version)
    private final Map<ToolboxId, String> defaultVersion = new ConcurrentHashMap<>(); // id -> 稳定版（默认最早注册版）
    private Clock clock = Clock.systemUTC();

    @Override
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /** 注册一个版本（同一 id 多版本并存）。首个注册的版本为稳定默认版。 */
    @Override
    public void register(ToolboxDefinition def) {
        store.computeIfAbsent(def.getId(), k -> new TreeMap<>(Comparator.reverseOrder()))
             .put(def.getVersion(), def);
        defaultVersion.putIfAbsent(def.getId(), def.getVersion());
    }

    @Override
    public TreeMap<String, ToolboxDefinition> versions(ToolboxId id) {
        return store.get(id);
    }

    /** 灰度路由：指定 caller 访问某 id 时固定走 version。 */
    @Override
    public void setGrayRoute(ToolboxId id, String callerId, String version) {
        grayRoutes.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(callerId, version);
    }

    /**
     * 发现层：返回 caller 可见且命中的版本（灰度优先）。
     * callerScopes 含 "*" 视为管理员，绕过 scope 过滤。
     */
    @Override
    public List<ToolboxDefinition> list(String callerId, List<String> callerScopes, String tenantId) {
        boolean admin = callerScopes != null && callerScopes.contains("*");
        List<ToolboxDefinition> result = new ArrayList<>();
        for (Map.Entry<ToolboxId, TreeMap<String, ToolboxDefinition>> entry : store.entrySet()) {
            ToolboxDefinition latest = entry.getValue().firstEntry().getValue();
            if (!visible(latest, callerScopes, tenantId, admin)) {
                continue;
            }
            String routeVer = grayRoutes.getOrDefault(entry.getKey(), Collections.emptyMap()).get(callerId);
            String chosenVer = routeVer != null ? routeVer : defaultVersion.get(entry.getKey());
            ToolboxDefinition chosen = chosenVer != null ? entry.getValue().get(chosenVer) : latest;
            if (chosen != null) {
                result.add(chosen);
            }
        }
        return result;
    }

    private boolean visible(ToolboxDefinition def, List<String> callerScopes, String tenantId, boolean admin) {
        if (def.getTenantId() != null && tenantId != null && !def.getTenantId().equals(tenantId)) {
            return false; // 租户隔离
        }
        if (def.getStatus() == ToolboxStatus.REMOVED) {
            return false; // 已移除不出现在发现列表
        }
        if (admin) {
            return true;
        }
        if (def.getRequiredScope() == null) {
            return true;
        }
        return callerScopes != null && callerScopes.contains(def.getRequiredScope());
    }

    /**
     * 调用层：返回 caller 路由到的版本定义；无权/已移除抛异常。
     */
    @Override
    public ToolboxDefinition resolve(String callerId, List<String> callerScopes, ToolboxId id, String requestedVersion) {
        TreeMap<String, ToolboxDefinition> versions = store.get(id);
        if (versions == null) {
            throw new IllegalArgumentException("Toolbox 不存在: " + id);
        }
        String routeVer = grayRoutes.getOrDefault(id, Collections.emptyMap()).get(callerId);
        String ver = routeVer != null ? routeVer
                : (requestedVersion != null ? requestedVersion : defaultVersion.getOrDefault(id, versions.firstKey()));
        ToolboxDefinition def = versions.get(ver);
        if (def == null) {
            throw new IllegalArgumentException("版本不存在: " + id + "@" + ver);
        }
        if (def.getStatus() == ToolboxStatus.REMOVED) {
            throw new IllegalStateException("Toolbox 已移除: " + id + "，迁移指引见 " + def.getDocUrl());
        }
        boolean admin = callerScopes != null && callerScopes.contains("*");
        if (!admin && def.getRequiredScope() != null && (callerScopes == null || !callerScopes.contains(def.getRequiredScope()))) {
            throw new SecurityException("caller 无 scope: " + def.getRequiredScope() + "，拦截调用 " + id);
        }
        return def;
    }

    /** 标记弃用（全部版本同时标期）。 */
    @Override
    public void deprecate(ToolboxId id) {
        TreeMap<String, ToolboxDefinition> versions = store.get(id);
        if (versions == null) {
            return;
        }
        Instant now = clock.instant();
        for (ToolboxDefinition d : versions.values()) {
            d.setStatus(ToolboxStatus.DEPRECATED);
            d.setDeprecatedAt(now);
        }
    }

    /** 推进弃用状态机：宽限 >=14天 的 DEPRECATED 转 REMOVED（stub）。 */
    @Override
    public void advanceLifecycle() {
        Instant now = clock.instant();
        for (TreeMap<String, ToolboxDefinition> versions : store.values()) {
            for (ToolboxDefinition d : versions.values()) {
                if (d.getStatus() == ToolboxStatus.DEPRECATED && d.getDeprecatedAt() != null) {
                    if (ChronoUnit.DAYS.between(d.getDeprecatedAt(), now) >= 14) {
                        d.setStatus(ToolboxStatus.REMOVED);
                    }
                }
            }
        }
    }
}
