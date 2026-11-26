package com.sentinel.aml.alert_case_service.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * How one audit entry is hashed (Phase 7b). Every field that matters is in the hash, plus the previous
 * entry's hash, so changing, removing or reordering any entry changes every hash after it.
 * Kept as a pure function: the appender and the verifier must compute exactly the same bytes.
 */
public final class AuditChain {

    /** prev_hash of the first entry. */
    public static final String GENESIS = "0".repeat(64);

    /** Fixed width, so the text is identical however many trailing zeros the micros have. */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    private AuditChain() {}

    public record Entry(long seq, String eventId, LocalDateTime occurredAt, String service, String actor,
                        String action, String entityType, String entityId, String details, String prevHash) {}

    public static String hash(Entry e) {
        String material = String.join("\u001f", // unit separator: cannot appear in normal field values
                Long.toString(e.seq()), e.eventId(), TS.format(e.occurredAt()), e.service(), e.actor(),
                e.action(), e.entityType(), e.entityId(), e.details(), e.prevHash());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is always available", impossible);
        }
    }
}
