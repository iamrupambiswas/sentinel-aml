package com.sentinel.aml.alert_case_service.live;

import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.AlertChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps this pod's open SSE connections and pushes every alert change to them (Phase 7).
 *
 * Changes arrive from the {@code sentinel.alerts.changed} topic, not straight from this pod's own
 * ingest: with several pods, an alert created on pod A must still reach a browser connected to pod B.
 */
@Service
@Slf4j
public class AlertStreamService {

    /** The connection ends after this; the browser's EventSource reconnects on its own. */
    static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        register(emitter);
        send(emitter, SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    @JmsListener(destination = MessagingConstants.ALERTS_CHANGED_TOPIC, containerFactory = "topicListenerFactory")
    public void onAlertChanged(AlertChangedEvent event) {
        broadcast(SseEmitter.event().name("alert").id(event.alertId()).data(event));
    }

    /** A comment line every 25 s keeps idle connections from being closed by proxies and load balancers. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        broadcast(SseEmitter.event().comment("keep-alive"));
    }

    void broadcast(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // Browser went away: drop it here; completeWithError also fires onError for the container
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }

    int connections() {
        return emitters.size();
    }
}
