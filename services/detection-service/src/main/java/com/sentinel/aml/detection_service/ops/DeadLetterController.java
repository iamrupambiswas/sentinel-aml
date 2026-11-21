package com.sentinel.aml.detection_service.ops;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.dto.ApiResponse;
import com.sentinel.aml.detection_service.ops.DeadLetterService.DeadLetter;
import com.sentinel.aml.detection_service.ops.DeadLetterService.DeadLetterQueueInfo;
import com.sentinel.aml.detection_service.ops.DeadLetterService.ReplayResult;
import com.sentinel.aml.common.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Dead-letter operations (P21). Lives in detection-service because it already talks to the broker;
 * it works for every sentinel queue. ADMIN only (config/SecurityRoutes).
 */
@RestController
@RequestMapping(ApiRoutes.OPS_DLQ)
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetters;
    private final AuditPublisher audit;
    private final JmsTemplate jmsTemplate;

    @Value("${sentinel.ops.poison-test-enabled:false}")
    private boolean poisonTestEnabled;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeadLetterQueueInfo>>> overview() {
        return ok(deadLetters.overview(), null);
    }

    @GetMapping("/{queue}")
    public ResponseEntity<ApiResponse<List<DeadLetter>>> browse(@PathVariable("queue") String queue,
                                                                @RequestParam(name = "max", defaultValue = "50") int max) {
        return ok(deadLetters.browse(queue, Math.min(Math.max(max, 1), 500)), null);
    }

    /** Replays one message (messageId) or all of them (up to max) back to the source queue. */
    @PostMapping("/{queue}/replay")
    public ResponseEntity<ApiResponse<ReplayResult>> replay(@PathVariable("queue") String queue,
                                                            @RequestParam(name = "messageId", required = false) String messageId,
                                                            @RequestParam(name = "max", defaultValue = "100") int max) {
        ReplayResult result = deadLetters.replay(queue, messageId, Math.min(Math.max(max, 1), 1000));
        audit.record("DLQ_REPLAYED", "DEAD_LETTER", queue, Map.of("replayed", result.replayed(), "messageIds", result.messageIds()));
        return ok(result, "Replayed " + result.replayed() + " message(s) to " + queue);
    }

    @DeleteMapping("/{queue}/messages/{messageId}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable("queue") String queue,
                                                      @PathVariable("messageId") String messageId) {
        deadLetters.delete(queue, messageId);
        audit.record("DLQ_MESSAGE_DELETED", "DEAD_LETTER", queue, Map.of("messageId", messageId));
        return ok(messageId, "Dead letter deleted permanently");
    }

    /**
     * LOCAL DEMO ONLY (sentinel.ops.poison-test-enabled): sends a message the consumer can never read,
     * so you can watch 5 delivery attempts with backoff and the move to the DLQ (about 15 s).
     */
    @PostMapping("/{queue}/poison-test")
    public ResponseEntity<ApiResponse<String>> poison(@PathVariable("queue") String queue) {
        if (!poisonTestEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poison test is disabled");
        }
        if (!DeadLetterService.SOURCE_QUEUES.contains(queue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown queue " + queue);
        }
        String type = MessagingConstants.TRANSACTION_QUEUE.equals(queue)
                ? MessagingConstants.TYPE_TRANSACTION_INGESTED : MessagingConstants.TYPE_RULE_HITS_DETECTED;
        jmsTemplate.send(queue, session -> {
            var m = session.createTextMessage("{\"this is\": \"not a valid " + type + " event\", \"amountInr\": \"abc\"");
            m.setStringProperty(MessagingConstants.TYPE_HEADER, type);
            return m;
        });
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.<String>builder().success(true)
                .message("Poison message sent to " + queue + "; expect it in " + DeadLetterService.dlqName(queue)
                        + " after 5 attempts (~15 s)")
                .data(queue).build());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.<T>builder().success(true).message(message).data(data).build());
    }
}
