package com.sentinel.aml.common.constants;

/** Redis keys shared by more than one service. Keys owned by a single service stay in that service. */
public final class RedisKeys {

    private RedisKeys() {}

    /**
     * {@code aml:acct-risk:{accountId}} -> LOW | MEDIUM | HIGH.
     * Written by customer-service (owner of risk ratings), read by detection-service for the score
     * multiplier (LOW 1.0 / MEDIUM 1.15 / HIGH 1.3). No TTL: it is a mirror of customer_db, kept in
     * sync on every change plus a full resync at startup.
     */
    public static final String ACCOUNT_RISK_PREFIX = "aml:acct-risk:";

    public static String accountRisk(String accountId) {
        return ACCOUNT_RISK_PREFIX + accountId;
    }
}
