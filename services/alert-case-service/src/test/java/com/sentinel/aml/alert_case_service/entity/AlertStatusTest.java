package com.sentinel.aml.alert_case_service.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertStatusTest {

    @Test
    void newAlertCanBeReviewedOrClosedButNotEscalatedDirectly() {
        assertThat(AlertStatus.NEW.canMoveTo(AlertStatus.UNDER_REVIEW)).isTrue();
        assertThat(AlertStatus.NEW.canMoveTo(AlertStatus.CLOSED_FALSE_POSITIVE)).isTrue();
        assertThat(AlertStatus.NEW.canMoveTo(AlertStatus.ESCALATED)).isFalse();
    }

    @Test
    void escalatedCanOnlyBeClosed() {
        assertThat(AlertStatus.ESCALATED.allowedNext())
                .containsExactlyInAnyOrder(AlertStatus.CLOSED_FALSE_POSITIVE, AlertStatus.CLOSED_CONFIRMED);
    }

    @Test
    void closedStatesAreTerminal() {
        for (AlertStatus closed : new AlertStatus[]{AlertStatus.CLOSED_FALSE_POSITIVE, AlertStatus.CLOSED_CONFIRMED}) {
            assertThat(closed.isClosed()).isTrue();
            assertThat(closed.allowedNext()).isEmpty();
        }
    }
}
