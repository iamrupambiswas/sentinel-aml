package com.sentinel.aml.gateway_service.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token bucket per caller, kept in Redis so every gateway pod shares the same budget.
 * The whole read-refill-take step is one Lua script: two concurrent requests from the same caller
 * cannot both spend the last token.
 */
@Component
public class RedisRateLimiter {

    static final String KEY_PREFIX = "rl:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final StringRedisTemplate redis;
    private final long capacity;
    private final double refillPerSecond;

    public RedisRateLimiter(StringRedisTemplate redis,
                            @Value("${sentinel.rate-limit.capacity:100}") long capacity,
                            @Value("${sentinel.rate-limit.refill-per-second:20}") double refillPerSecond) {
        if (capacity < 1 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("Rate limit needs capacity >= 1 and refill-per-second > 0");
        }
        this.redis = redis;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    public record Decision(boolean allowed, long remaining, long retryAfterMillis) {}

    public Decision tryConsume(String caller) {
        List<?> r = redis.execute(SCRIPT, List.of(KEY_PREFIX + caller),
                Long.toString(capacity), Double.toString(refillPerSecond));
        return new Decision(((Number) r.get(0)).longValue() == 1,
                ((Number) r.get(1)).longValue(),
                ((Number) r.get(2)).longValue());
    }

    public long capacity() {
        return capacity;
    }
}
