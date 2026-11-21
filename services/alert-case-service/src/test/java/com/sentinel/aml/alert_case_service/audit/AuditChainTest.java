package com.sentinel.aml.alert_case_service.audit;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChainTest {

    private static AuditChain.Entry entry(String actor, String prev) {
        return new AuditChain.Entry(7, "e-1", LocalDateTime.of(2026, 11, 21, 10, 0, 0, 120_000_000),
                "detection-service", actor, "RULE_CHANGED", "RULE", "CTR_THRESHOLD", "{\"version\":2}", prev);
    }

    @Test
    void sameEntryGivesSameHashAndEveryFieldMatters() {
        String h = AuditChain.hash(entry("admin1", AuditChain.GENESIS));
        assertThat(h).hasSize(64).isEqualTo(AuditChain.hash(entry("admin1", AuditChain.GENESIS)));
        assertThat(AuditChain.hash(entry("mallory", AuditChain.GENESIS))).isNotEqualTo(h);
        assertThat(AuditChain.hash(entry("admin1", "f".repeat(64)))).isNotEqualTo(h);   // linked to the previous entry
    }

    @Test
    void timestampTextIsFixedWidthSoTrailingZerosCannotChangeTheHash() {
        // .12 s and .120000 s are the same instant; the fixed-width format makes them the same bytes
        AuditChain.Entry a = entry("admin1", AuditChain.GENESIS);
        AuditChain.Entry b = new AuditChain.Entry(a.seq(), a.eventId(), LocalDateTime.parse("2026-11-21T10:00:00.120000"),
                a.service(), a.actor(), a.action(), a.entityType(), a.entityId(), a.details(), a.prevHash());
        assertThat(AuditChain.hash(b)).isEqualTo(AuditChain.hash(a));
    }
}
