package com.sentinel.aml.alert_case_service.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Analyst workflow. Closed states are terminal: if the same pattern keeps going in the same
 * window, new hits are still attached (hit_count grows) but the alert is not reopened, because
 * an analyst already decided on that pattern. A new window produces a new alert.
 */
public enum AlertStatus {
    NEW,
    UNDER_REVIEW,
    ESCALATED,
    CLOSED_FALSE_POSITIVE,
    CLOSED_CONFIRMED;

    public boolean isClosed() {
        return this == CLOSED_FALSE_POSITIVE || this == CLOSED_CONFIRMED;
    }

    public Set<AlertStatus> allowedNext() {
        return switch (this) {
            case NEW -> EnumSet.of(UNDER_REVIEW, CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED);
            case UNDER_REVIEW -> EnumSet.of(ESCALATED, CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED);
            case ESCALATED -> EnumSet.of(CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED);
            case CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED -> EnumSet.noneOf(AlertStatus.class);
        };
    }

    public boolean canMoveTo(AlertStatus next) {
        return allowedNext().contains(next);
    }
}
