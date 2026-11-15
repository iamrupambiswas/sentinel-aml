package com.sentinel.aml.alert_case_service.service;

import com.sentinel.aml.common.event.RuleHitPayload;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X2 / P14: 50 threads, 600 deliveries (300 events x 2 - every event is delivered twice, like
 * at-least-once redelivery), all racing on the same 24 alert keys. Runs against a real MySQL,
 * because the guarantee comes from MySQL's unique index and row locks - an in-memory DB or mocks
 * would prove nothing.
 *
 * Expected: exactly one alert per key, every hit attached exactly once, counters exact.
 */
@Testcontainers(disabledWithoutDocker = true)   // skipped, not failed, on machines without Docker
@SpringBootTest(properties = {
        "spring.jms.listener.auto-startup=false"   // no broker needed: we call the ingest service directly
})
class AlertDedupConcurrencyTest {

    private static final int THREADS = 50;
    private static final int EVENTS = 300;
    private static final String[] ACCOUNTS = {"ACC-C1", "ACC-C2", "ACC-C3", "ACC-C4", "ACC-C5", "ACC-C6"};
    private static final String[] RULES = {"STRUCTURING", "RAPID_MOVEMENT"};
    private static final String[] BUCKETS = {"20521", "20522"};

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("alert_case_db");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?useAffectedRows=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "60");
    }

    @Autowired
    AlertIngestService ingestService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM alert_hit");
        jdbc.update("DELETE FROM alert");
    }

    @Test
    void fiftyThreadsWithDuplicateDeliveriesProduceExactlyOneAlertPerKeyAndExactCounts() throws Exception {
        Random random = new Random(42);
        List<RuleHitsDetectedEvent> events = new ArrayList<>();
        Map<String, Integer> expectedHits = new HashMap<>();          // dedupKey -> distinct hits
        Map<String, BigDecimal> expectedMaxScore = new HashMap<>();   // dedupKey -> max score
        Instant t0 = Instant.parse("2026-11-15T10:00:00Z");

        for (int i = 0; i < EVENTS; i++) {
            String account = ACCOUNTS[random.nextInt(ACCOUNTS.length)];
            String bucket = BUCKETS[random.nextInt(BUCKETS.length)];
            BigDecimal score = BigDecimal.valueOf(10 + random.nextInt(9000) / 100.0).setScale(2, RoundingMode.HALF_UP);
            // 1 or 2 rules per event: multi-hit events lock two alert rows (deadlock-prone if unordered)
            List<RuleHitPayload> hits = new ArrayList<>();
            List<String> rules = new ArrayList<>(List.of(RULES));
            Collections.shuffle(rules, random);
            int n = 1 + random.nextInt(2);
            for (int h = 0; h < n; h++) {
                hits.add(new RuleHitPayload(rules.get(h), new BigDecimal("70.00"), "hit " + i, List.of("txn-" + i), bucket));
                String key = account + ":" + rules.get(h) + ":" + bucket;
                expectedHits.merge(key, 1, Integer::sum);
                expectedMaxScore.merge(key, score, BigDecimal::max);
            }
            events.add(new RuleHitsDetectedEvent(UUID.randomUUID().toString(), "txn-" + i, account, hits, score,
                    "explanation " + i, t0.plusSeconds(i), t0.plusSeconds(i + 1)));
        }

        // At-least-once delivery: every event arrives twice, in random order
        List<RuleHitsDetectedEvent> deliveries = new ArrayList<>(events);
        deliveries.addAll(events);
        Collections.shuffle(deliveries, random);

        AtomicInteger created = new AtomicInteger();
        AtomicInteger merged = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (RuleHitsDetectedEvent event : deliveries) {
            pool.submit(() -> {
                try {
                    start.await();
                    // Mirrors the broker: a deadlock victim's transaction rolls back and is redelivered
                    for (int attempt = 1; ; attempt++) {
                        try {
                            AlertIngestService.Outcome o = ingestService.ingest(event);
                            created.addAndGet(o.created());
                            merged.addAndGet(o.merged());
                            duplicates.addAndGet(o.duplicates());
                            return;
                        } catch (ConcurrencyFailureException e) {
                            if (attempt == 5) throw e;
                            retries.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        long began = System.currentTimeMillis();
        start.countDown();   // release all 50 threads at once
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).as("finished in time").isTrue();
        long tookMs = System.currentTimeMillis() - began;

        assertThat(failures).as("no delivery failed permanently").isEmpty();

        int distinctHits = expectedHits.values().stream().mapToInt(Integer::intValue).sum();
        Set<String> keys = new HashSet<>(expectedHits.keySet());

        // 1. exactly one alert per (account, rule, window)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alert", Integer.class)).isEqualTo(keys.size());
        assertThat(created.get()).isEqualTo(keys.size());

        // 2. every hit attached exactly once despite 2x delivery
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alert_hit", Integer.class)).isEqualTo(distinctHits);
        assertThat(created.get() + merged.get()).isEqualTo(distinctHits);
        assertThat(duplicates.get()).isEqualTo(distinctHits);   // the second copy of every hit was recognised

        // 3. counters exact per alert: no lost increments, true max score
        for (String key : keys) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, hit_count, risk_score FROM alert WHERE dedup_key = ?", key);
            int attached = jdbc.queryForObject("SELECT COUNT(*) FROM alert_hit WHERE alert_id = ?",
                    Integer.class, row.get("id"));
            assertThat(((Number) row.get("hit_count")).intValue()).as("hit_count of " + key).isEqualTo(expectedHits.get(key));
            assertThat(attached).as("attached hits of " + key).isEqualTo(expectedHits.get(key));
            assertThat((BigDecimal) row.get("risk_score")).as("max score of " + key)
                    .isEqualByComparingTo(expectedMaxScore.get(key));
        }

        System.out.printf("X2: %d deliveries on %d threads -> %d alerts, %d hits, %d duplicates ignored, "
                        + "%d deadlock retries, %d ms%n",
                deliveries.size(), THREADS, keys.size(), distinctHits, duplicates.get(), retries.get(), tookMs);
    }
}
