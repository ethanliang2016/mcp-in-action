package com.ethanliang.mcp.multitenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 凭证隔离：Key → tenantId 映射存 Redis（tenant:key:{hash} → tenantId）。
 *
 * 吊销/轮换：删掉这个 Redis 键即生效，无需重启网关——这是集中存的好处。
 * 本模块启动时把 yml 里的租户 seed 进 Redis，保证一键复现（真实环境由控制面下发）。
 */
@Component
public class TenantStore {

    private final StringRedisTemplate redis;
    private final TenantProperties properties;
    private final Map<String, TenantProperties.TenantDef> byKey = new ConcurrentHashMap<>();
    private final Map<String, TenantProperties.TenantDef> byId = new ConcurrentHashMap<>();

    public TenantStore(StringRedisTemplate redis, TenantProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @PostConstruct
    public void seed() {
        for (TenantProperties.TenantDef tenant : properties.getTenants()) {
            byKey.put(tenant.getKey(), tenant);
            byId.put(tenant.getId(), tenant);
            try {
                redis.opsForValue().set("tenant:key:" + hash(tenant.getKey()), tenant.getId(), Duration.ofDays(90));
            } catch (Exception ignored) {
                // Redis 未就绪不阻塞启动（配额与映射会在运行时重新尝试）
            }
        }
    }

    /** 解析租户：先命中本地配置，再查 Redis 映射（Redis 里的映射才是可动态吊销的那一层） */
    public TenantProperties.TenantDef resolve(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        TenantProperties.TenantDef local = byKey.get(apiKey);
        if (local != null) {
            return local;
        }
        try {
            String id = redis.opsForValue().get("tenant:key:" + hash(apiKey));
            return id == null ? null : byId.get(id);
        } catch (Exception e) {
            return null;
        }
    }

    public TenantProperties.TenantDef byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /** 缓存/映射键统一前缀，避免不同租户同 key 串数据（数据隔离的键规范） */
    public static String tenantScoped(String tenantId, String key) {
        return "tenant:" + tenantId + ":" + key;
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
