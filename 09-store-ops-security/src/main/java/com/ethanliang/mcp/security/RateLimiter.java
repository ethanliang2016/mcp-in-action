package com.ethanliang.mcp.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L5 限流：按用户维度限流（token 桶）。
 *
 * 演示规格：每用户 5 次 / 10 秒。触发限流返回 429。
 */
public final class RateLimiter {

    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private static Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofSeconds(10)));
        return Bucket.builder().addLimit(limit).build();
    }

    /** 尝试消费 1 个令牌；false = 已限流 */
    public static boolean tryConsume(String userId) {
        return BUCKETS.computeIfAbsent(userId, k -> newBucket()).tryConsume(1);
    }
}
