package com.sentinel.aml.detection_service.service.impl;

import com.sentinel.aml.detection_service.service.VelocityWindowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityWindowServiceImpl implements VelocityWindowService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public BigDecimal calculateWindowSum(String accountId, String transactionId, BigDecimal amount, long windowMillis) {
        String redisKey = "aml:velocity:24h:" + accountId;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - windowMillis;
        String member = transactionId + ":" + amount.toPlainString();

        // 1. Prune aged-out transactions (ZREMRANGEBYSCORE)
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart - 1);

        // 2. Add current transaction (Overwrites if phantom redelivery occurs)
        redisTemplate.opsForZSet().add(redisKey, member, currentTime);

        // 3. Set TTL to free up RAM if the account goes dormant
        redisTemplate.expire(redisKey, Duration.ofMillis(windowMillis));

        // 4. Calculate the sum of all valid transactions remaining in the window
        Set<String> activeTransactions = redisTemplate.opsForZSet().range(redisKey, 0, -1);
        BigDecimal totalVelocity = BigDecimal.ZERO;

        if (activeTransactions != null) {
            for (String tx : activeTransactions) {
                String[] parts = tx.split(":");
                if (parts.length == 2) {
                    totalVelocity = totalVelocity.add(new BigDecimal(parts[1]));
                }
            }
        }

        return totalVelocity;
    }
}