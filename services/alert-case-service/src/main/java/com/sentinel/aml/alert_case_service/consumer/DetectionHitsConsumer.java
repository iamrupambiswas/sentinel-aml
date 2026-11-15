package com.sentinel.aml.alert_case_service.consumer;

import com.sentinel.aml.alert_case_service.service.AlertIngestService;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * The only listener on sentinel.detection.hits. Any exception (DB down, deadlock) rolls the
 * transacted JMS session back; the broker redelivers with backoff and, after the max attempts,
 * parks the message in DLQ.sentinel.detection.hits (P21). Ingest is idempotent, so redelivery is safe.
 */
@Component
@RequiredArgsConstructor
public class DetectionHitsConsumer {

    private final AlertIngestService ingestService;
    private final MeterRegistry meterRegistry;

    @JmsListener(destination = MessagingConstants.DETECTION_HITS_QUEUE, concurrency = "${sentinel.alerts.concurrency:4-8}")
    public void onHits(RuleHitsDetectedEvent event) {
        AlertIngestService.Outcome outcome = ingestService.ingest(event);
        meterRegistry.counter("sentinel_alerts_created_total").increment(outcome.created());
        meterRegistry.counter("sentinel_alert_hits_merged_total").increment(outcome.merged());
        meterRegistry.counter("sentinel_alert_hits_duplicate_total").increment(outcome.duplicates());
    }
}
