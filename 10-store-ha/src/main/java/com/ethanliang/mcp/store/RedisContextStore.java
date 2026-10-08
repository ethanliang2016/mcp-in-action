package com.ethanliang.mcp.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 外置存储 —— 多实例共享（与篇八一致）。
 * 高可用篇里 Redis 连接走哨兵（见 application-docker.yml），避免 Redis 本身成为单点。
 */
@Component
@ConditionalOnProperty(name = "store.context.type", havingValue = "redis")
public class RedisContextStore implements ContextStore {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public RedisContextStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String key, String value) {
        redis.opsForValue().set("ctx:" + key, value, TTL);
    }

    @Override
    public String load(String key) {
        return redis.opsForValue().get("ctx:" + key);
    }

    @Override
    public String kind() {
        return "redis(外置共享/哨兵)";
    }
}
