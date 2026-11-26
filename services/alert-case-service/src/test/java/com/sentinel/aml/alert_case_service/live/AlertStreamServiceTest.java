package com.sentinel.aml.alert_case_service.live;

import com.sentinel.aml.common.event.AlertChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertStreamServiceTest {

    /** Records what would go over the wire, or fails like a closed browser connection. */
    static class FakeEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        final boolean broken;

        FakeEmitter(boolean broken) {
            this.broken = broken;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) throw new IOException("Broken pipe");
            // The SSE text of the event ("event:alert\nid:..."), without the JSON payload object
            StringBuilder text = new StringBuilder();
            builder.build().forEach(d -> { if (d.getData() instanceof String str) text.append(str); });
            sent.add(text.toString());
        }
    }

    private static AlertChangedEvent change() {
        return new AlertChangedEvent("a-1", "ACC-1", "CTR_THRESHOLD",
                AlertChangedEvent.Change.CREATED, "NEW", Instant.parse("2026-11-21T10:00:00Z"));
    }

    @Test
    void everyConnectedBrowserGetsTheChange() {
        AlertStreamService service = new AlertStreamService();
        FakeEmitter a = new FakeEmitter(false), b = new FakeEmitter(false);
        service.register(a);
        service.register(b);

        service.onAlertChanged(change());

        assertThat(a.sent).hasSize(1);
        assertThat(b.sent).hasSize(1);
        assertThat(a.sent.get(0)).contains("event:alert").contains("id:a-1");
    }

    @Test
    void aClosedConnectionIsDroppedAndDoesNotStopTheOthers() {
        AlertStreamService service = new AlertStreamService();
        FakeEmitter dead = new FakeEmitter(true), alive = new FakeEmitter(false);
        service.register(dead);
        service.register(alive);

        service.onAlertChanged(change());

        assertThat(alive.sent).hasSize(1);
        assertThat(service.connections()).isEqualTo(1);
    }

    @Test
    void heartbeatGoesToEveryone() {
        AlertStreamService service = new AlertStreamService();
        FakeEmitter a = new FakeEmitter(false);
        service.register(a);

        service.heartbeat();

        assertThat(a.sent).hasSize(1);
        assertThat(a.sent.get(0)).contains(":keep-alive");
    }
}
