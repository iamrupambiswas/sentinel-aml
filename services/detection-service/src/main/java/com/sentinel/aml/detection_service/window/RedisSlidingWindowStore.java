package com.sentinel.aml.detection_service.window;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class RedisSlidingWindowStore implements SlidingWindowStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final String ACTIVE_ACCOUNTS = "aml:accounts:active";

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> windowScript;

    public RedisSlidingWindowStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.windowScript = new DefaultRedisScript<>();
        this.windowScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/sliding_window.lua")));
        this.windowScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WindowEntry> addAndGet(String key, WindowEntry entry, Duration window) {
        long ttl = window.plusHours(1).toMillis();
        List<Object> raw = redis.execute(windowScript, List.of(key),
                String.valueOf(entry.timestampMillis()), entry.encode(),
                String.valueOf(window.toMillis()), String.valueOf(ttl));
        return decodeWithScores(raw);
    }

    @Override
    public List<WindowEntry> read(String key, long fromMillis, long toMillis) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeByScoreWithScores(key, fromMillis, toMillis);
        List<WindowEntry> out = new ArrayList<>();
        if (tuples != null) {
            for (var t : tuples) {
                out.add(WindowEntry.decode(t.getValue(), t.getScore() == null ? 0 : t.getScore().longValue()));
            }
        }
        return out;
    }

    @Override
    public DailyTotal addToDay(String accountId, LocalDate day, String transactionId, BigDecimal amountInr, Duration ttl) {
        String key = dayKey(accountId, day);
        redis.opsForHash().put(key, transactionId, amountInr.toPlainString()); // HSET is idempotent per txn
        redis.expire(key, ttl);
        Map<Object, Object> all = redis.opsForHash().entries(key);
        BigDecimal total = BigDecimal.ZERO;
        List<String> ids = new ArrayList<>();
        for (var e : all.entrySet()) {
            ids.add(e.getKey().toString());
            total = total.add(new BigDecimal(e.getValue().toString()));
        }
        Collections.sort(ids);
        return new DailyTotal(total, ids);
    }

    @Override
    public Map<LocalDate, BigDecimal> dailyTotals(String accountId, LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) days.add(d);

        // One pipelined round trip for all ~90 days
        List<Object> results = redis.executePipelined((RedisCallback<Object>) conn -> {
            StringRedisConnection c = (StringRedisConnection) conn;
            for (LocalDate d : days) c.hVals(dayKey(accountId, d));
            return null;
        });

        Map<LocalDate, BigDecimal> totals = new TreeMap<>();
        for (int i = 0; i < days.size(); i++) {
            Object r = results.get(i);
            if (r instanceof Collection<?> values && !values.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (Object v : values) sum = sum.add(new BigDecimal(v.toString()));
                totals.put(days.get(i), sum);
            }
        }
        return totals;
    }

    @Override
    public BigDecimal getBaseline(String accountId) {
        String v = redis.opsForValue().get("aml:baseline:" + accountId);
        return v == null ? null : new BigDecimal(v);
    }

    @Override
    public void putBaseline(String accountId, BigDecimal avgDailyInr, Duration ttl) {
        redis.opsForValue().set("aml:baseline:" + accountId, avgDailyInr.toPlainString(), ttl);
    }

    @Override
    public void markAccountActive(String accountId) {
        redis.opsForSet().add(ACTIVE_ACCOUNTS, accountId);
    }

    @Override
    public Set<String> activeAccounts() {
        Set<String> members = redis.opsForSet().members(ACTIVE_ACCOUNTS);
        return members == null ? Set.of() : members;
    }

    private static String dayKey(String accountId, LocalDate day) {
        return "aml:day:" + accountId + ":" + DAY.format(day);
    }

    private static List<WindowEntry> decodeWithScores(List<Object> raw) {
        List<WindowEntry> out = new ArrayList<>();
        if (raw == null) return out;
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            String member = raw.get(i).toString();
            long score = (long) Double.parseDouble(raw.get(i + 1).toString());
            out.add(WindowEntry.decode(member, score));
        }
        return out;
    }
}
