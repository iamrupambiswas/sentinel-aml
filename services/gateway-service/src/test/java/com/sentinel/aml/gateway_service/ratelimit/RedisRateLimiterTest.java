package com.sentinel.aml.gateway_service.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The token bucket Lua script against a real Redis (Testcontainers). Skipped without Docker. */
class RedisRateLimiterTest {

    static GenericContainer<?> redisContainer;
    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        redisContainer = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);
        redisContainer.start();
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void stop() {
        if (factory != null) factory.destroy();
        if (redisContainer != null) redisContainer.stop();
    }

    private static String caller() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void allowsTheBurstThenDenies() {
        RedisRateLimiter limiter = new RedisRateLimiter(redis, 3, 0.001); // practically no refill
        String c = caller();

        assertThat(limiter.tryConsume(c).allowed()).isTrue();
        assertThat(limiter.tryConsume(c).allowed()).isTrue();
        RedisRateLimiter.Decision third = limiter.tryConsume(c);
        assertThat(third.allowed()).isTrue();
        assertThat(third.remaining()).isZero();

        RedisRateLimiter.Decision denied = limiter.tryConsume(c);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterMillis()).isPositive();
    }

    @Test
    void refillsOverTime() throws Exception {
        RedisRateLimiter limiter = new RedisRateLimiter(redis, 1, 20); // one token every 50 ms
        String c = caller();
        assertThat(limiter.tryConsume(c).allowed()).isTrue();
        assertThat(limiter.tryConsume(c).allowed()).isFalse();
        Thread.sleep(120);
        assertThat(limiter.tryConsume(c).allowed()).isTrue();
    }

    @Test
    void callersHaveSeparateBuckets() {
        RedisRateLimiter limiter = new RedisRateLimiter(redis, 1, 0.001);
        assertThat(limiter.tryConsume(caller()).allowed()).isTrue();
        assertThat(limiter.tryConsume(caller()).allowed()).isTrue();
    }

    @Test
    void concurrentRequestsNeverSpendMoreThanTheCapacity() throws Exception {
        RedisRateLimiter limiter = new RedisRateLimiter(redis, 20, 0.001);
        String c = caller();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            var tasks = IntStream.range(0, 100)
                    .mapToObj(i -> (Callable<Boolean>) () -> limiter.tryConsume(c).allowed()).toList();
            long allowed = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) allowed++;
            }
            assertThat(allowed).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }
}
