package com.sentinel.aml.risk;

import com.sentinel.aml.common.constants.RedisKeys;
import com.sentinel.aml.enums.RiskRating;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Writes aml:acct-risk:{accountId} (read by detection-service's CustomerRiskLookup).
 *
 * Redis is not part of the MySQL transaction, so single-account writes are deferred until AFTER
 * COMMIT: a rating change that rolls back must never reach detection. If Redis is down at that
 * moment the DB is still right, and the startup / admin resync repairs Redis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountRiskPublisher {

    private final StringRedisTemplate redis;

    public void publishAfterCommit(Map<String, RiskRating> ratings) {
        if (ratings.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safePublish(ratings);
                }
            });
        } else {
            safePublish(ratings);
        }
    }

    /** One pipelined round trip for the whole map (used by the bulk resync too). */
    public void publishNow(Map<String, RiskRating> ratings) {
        if (ratings.isEmpty()) return;
        redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            ratings.forEach((accountId, rating) -> c.set(RedisKeys.accountRisk(accountId), rating.name()));
            return null;
        });
    }

    private void safePublish(Map<String, RiskRating> ratings) {
        try {
            publishNow(ratings);
            log.info("Published effective risk rating for {} account(s) to Redis", ratings.size());
        } catch (Exception e) {
            log.error("Redis write failed for {} account(s); POST /api/v1/admin/risk-sync will repair it",
                    ratings.size(), e);
        }
    }
}
