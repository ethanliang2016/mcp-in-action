package com.ethanliang.mcp.multitenant;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 配额管理：Redis + Lua 原子自增 + TTL（固定窗口）。
 *
 * 为什么计数放 Redis：配额属于"下一次请求打到另一个副本还必须对"的数据，
 * 必须外置——这正是篇（八）"多实例下状态要外置"在配额上的落地。
 * 两个副本看到同一份数字，不会出现"副本A 限了、副本B 还放"的裂隙。
 *
 * 固定窗口（首调用起算 TTL）而非真滑动窗口；真滑动窗口需用 ZSET 记每次时间戳，
 * 本文为可读性用固定窗口近似，生产可按精度升级。
 */
@Service
public class QuotaService {

    /** INCR 计数；cnt==1 时设置窗口 TTL，返回当前计数 */
    private static final String LUA = """
            local cnt = redis.call('INCR', KEYS[1])
            if cnt == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return cnt
            """;

    private final StringRedisTemplate redis;
    private final TenantProperties properties;

    public QuotaService(StringRedisTemplate redis, TenantProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 是否放行。Redis 不可用时按租户降级策略：
     * closed = 全拒（保隔离，核心租户）/ open = 放行（保可用，长尾租户）。
     */
    public boolean allow(TenantProperties.TenantDef tenant) {
        String key = "quota:" + tenant.getId() + ":calls";
        try {
            Long cnt = redis.execute(
                    new DefaultRedisScript<>(LUA, Long.class),
                    List.of(key),
                    String.valueOf(properties.getQuotaWindowSeconds()));
            return cnt == null || cnt <= tenant.getQuotaLimit();
        } catch (Exception e) {
            // Redis 故障演练：按租户分级降级
            return "open".equalsIgnoreCase(tenant.getFailover());
        }
    }

    /** 剩余配额（观测用，Redis 不可用返回 -1） */
    public long remaining(TenantProperties.TenantDef tenant) {
        try {
            String raw = redis.opsForValue().get("quota:" + tenant.getId() + ":calls");
            long used = raw == null ? 0 : Long.parseLong(raw);
            return Math.max(0, tenant.getQuotaLimit() - used);
        } catch (Exception e) {
            return -1;
        }
    }
}
