package com.ethanliang.mcp.toolbox.registry;

import com.ethanliang.mcp.toolbox.model.ToolboxDefinition;
import com.ethanliang.mcp.toolbox.model.ToolboxId;
import com.ethanliang.mcp.toolbox.model.ToolboxStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 后端注册表（多实例一致性，profile=redis）。[Redis实测]
 *
 * 存储（StringRedisTemplate，无 Jackson 依赖）：
 *   - hash tb:{ownerTeamId}:{name}，field=version，value=字段以 \u0001 连接的编码串
 *   - hash tb:gray:{id}，field=callerId，value=version（灰度路由）
 *   - string tb:default:{id}=稳定版
 * 注册/弃用时向 channel toolbox-events 发 pub/sub 事件（RedisPubSubConfig 订阅记录失效）。
 * 读统一走 Redis —— 多个实例共享同一份状态，天然多实例一致。
 */
@Component
@Profile("redis")
public class RedisToolboxRegistry implements ToolboxRegistry {

    private static final String SEP = "";
    private final StringRedisTemplate redis;
    private Clock clock = Clock.systemUTC();

    public RedisToolboxRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private HashOperations<String, String, String> hash() {
        return redis.opsForHash();
    }

    private String key(ToolboxId id) {
        return "tb:" + id.ownerTeamId() + ":" + id.name();
    }

    private String grayKey(ToolboxId id) {
        return "tb:gray:" + id.ownerTeamId() + ":" + id.name();
    }

    private String defaultKey(ToolboxId id) {
        return "tb:default:" + id.ownerTeamId() + ":" + id.name();
    }

    @Override
    public void register(ToolboxDefinition def) {
        hash().put(key(def.getId()), def.getVersion(), encode(def));
        redis.boundValueOps(defaultKey(def.getId())).setIfAbsent(def.getVersion());
        publish(def.getId());
    }

    @Override
    public void setGrayRoute(ToolboxId id, String callerId, String version) {
        hash().put(grayKey(id), callerId, version);
    }

    @Override
    public TreeMap<String, ToolboxDefinition> versions(ToolboxId id) {
        Map<String, String> all = hash().entries(key(id));
        TreeMap<String, ToolboxDefinition> tm = new TreeMap<>(Comparator.reverseOrder());
        for (Map.Entry<String, String> e : all.entrySet()) {
            tm.put(e.getKey(), decode(e.getValue()));
        }
        return tm;
    }

    @Override
    public List<ToolboxDefinition> list(String callerId, List<String> callerScopes, String tenantId) {
        boolean admin = callerScopes != null && callerScopes.contains("*");
        List<ToolboxDefinition> result = new ArrayList<>();
        for (ToolboxId id : allToolboxIds()) {
            TreeMap<String, ToolboxDefinition> vers = versions(id);
            if (vers.isEmpty()) {
                continue;
            }
            ToolboxDefinition latest = vers.firstEntry().getValue();
            if (!visible(latest, callerScopes, tenantId, admin)) {
                continue;
            }
            String routeVer = (String) hash().get(grayKey(id), callerId);
            String dv = redis.opsForValue().get(defaultKey(id));
            String chosenVer = routeVer != null ? routeVer : dv;
            ToolboxDefinition chosen = chosenVer != null ? vers.get(chosenVer) : latest;
            if (chosen != null) {
                result.add(chosen);
            }
        }
        return result;
    }

    @Override
    public ToolboxDefinition resolve(String callerId, List<String> callerScopes, ToolboxId id, String requestedVersion) {
        TreeMap<String, ToolboxDefinition> vers = versions(id);
        if (vers.isEmpty()) {
            throw new IllegalArgumentException("Toolbox 不存在: " + id);
        }
        String routeVer = (String) hash().get(grayKey(id), callerId);
        String dv = redis.opsForValue().get(defaultKey(id));
        String ver = routeVer != null ? routeVer
                : (requestedVersion != null ? requestedVersion : (dv != null ? dv : vers.firstKey()));
        ToolboxDefinition def = vers.get(ver);
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

    @Override
    public void deprecate(ToolboxId id) {
        Instant now = clock.instant();
        for (ToolboxDefinition d : versions(id).values()) {
            d.setStatus(ToolboxStatus.DEPRECATED);
            d.setDeprecatedAt(now);
            hash().put(key(id), d.getVersion(), encode(d));
        }
        publish(id);
    }

    @Override
    public void advanceLifecycle() {
        Instant now = clock.instant();
        for (ToolboxId id : allToolboxIds()) {
            for (ToolboxDefinition d : versions(id).values()) {
                if (d.getStatus() == ToolboxStatus.DEPRECATED && d.getDeprecatedAt() != null
                        && ChronoUnit.DAYS.between(d.getDeprecatedAt(), now) >= 14) {
                    d.setStatus(ToolboxStatus.REMOVED);
                    hash().put(key(id), d.getVersion(), encode(d));
                }
            }
        }
    }

    private List<ToolboxId> allToolboxIds() {
        List<ToolboxId> ids = new ArrayList<>();
        for (Object k : redis.keys("tb:*")) {
            String key = (String) k;
            if (key.startsWith("tb:default:") || key.contains(":gray:")) {
                continue;
            }
            String[] p = key.split(":", 3);
            if (p.length == 3) {
                ids.add(ToolboxId.of(p[1], p[2]));
            }
        }
        return ids;
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

    private void publish(ToolboxId id) {
        redis.convertAndSend("toolbox-events", id.toString());
    }

    // —— 编码：无 Jackson 依赖，固定字段以 \u0001 连接 ——
    private String encode(ToolboxDefinition d) {
        return String.join(SEP,
                d.getOwnerTeamId(), d.getId().name(), d.getVersion(), String.join(",", d.getTags()),
                nz(d.getOwner()), nz(d.getRequiredScope()), d.getChecksum(), nz(d.getDocUrl()),
                nz(d.getTenantId()), d.getMethodSignature(), d.getParamSchemaJson(), d.getReturnSchemaJson(),
                d.getStatus().name(), d.getDeprecatedAt() == null ? "" : d.getDeprecatedAt().toString(),
                String.valueOf(d.getSuccessRate()), String.valueOf(d.getP99Millis()), String.valueOf(d.getErrorRate()));
    }

    private ToolboxDefinition decode(String s) {
        String[] f = s.split(SEP, -1);
        ToolboxId id = ToolboxId.of(f[0], f[1]);
        ToolboxDefinition d = new ToolboxDefinition(id, f[2],
                f[3].isEmpty() ? List.of() : List.of(f[3].split(",")),
                f[4], f[5].isEmpty() ? null : f[5], f[7].isEmpty() ? null : f[7],
                f[8].isEmpty() ? null : f[8], f[9], f[10], f[11]);
        d.setStatus(ToolboxStatus.valueOf(f[12]));
        if (!f[13].isEmpty()) {
            d.setDeprecatedAt(Instant.parse(f[13]));
        }
        d.setSuccessRate(Double.parseDouble(f[14]));
        d.setP99Millis(Double.parseDouble(f[15]));
        d.setErrorRate(Double.parseDouble(f[16]));
        return d;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
