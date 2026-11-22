package com.sentinel.aml.detection_service.ops;

import com.sentinel.aml.common.constants.MessagingConstants;
import jakarta.jms.Connection;
import jakarta.jms.InvalidDestinationException;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Browse, replay and delete dead letters (P21).
 *
 * The broker moves a message to DLQ.&lt;queue&gt; after max-delivery-attempts failures
 * (infra/artemis/broker.properties). Those DLQs are queues on the shared "DLQ" address, so they are
 * addressed by fully-qualified queue name "DLQ::DLQ.&lt;queue&gt;".
 *
 * Uses a fresh, NON-cached connection on purpose: Spring's CachingConnectionFactory keeps consumers
 * open between calls, and an idle cached consumer can sit on prefetched DLQ messages so nobody else
 * (not even the next replay call) can see them.
 *
 * Replay is receive-from-DLQ + send-to-queue in ONE transacted JMS session: a crash in the middle
 * rolls both back, so a message is never lost and never duplicated by the replay itself.
 */
@Service
@Slf4j
public class DeadLetterService {

    /** Only our own queues; the API must not become a generic "read any queue" endpoint. */
    public static final Set<String> SOURCE_QUEUES = Set.of(
            MessagingConstants.TRANSACTION_QUEUE, MessagingConstants.DETECTION_HITS_QUEUE);

    private static final int MAX_BODY = 4000;
    private static final long RECEIVE_TIMEOUT_MS = 500;

    private final ConnectionFactory connectionFactory;

    public DeadLetterService(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory instanceof CachingConnectionFactory caching
                && caching.getTargetConnectionFactory() != null
                ? caching.getTargetConnectionFactory()
                : connectionFactory;
    }

    public record DeadLetterQueueInfo(String sourceQueue, String deadLetterQueue, int depth) {}

    /** No delivery count: browsing is not a delivery (it always reads 0). Being here means max attempts were used. */
    public record DeadLetter(String messageId, String type, String originalQueue, Instant firstSentAt, String body) {}

    public record ReplayResult(String sourceQueue, int replayed, List<String> messageIds) {}

    public List<DeadLetterQueueInfo> overview() {
        List<DeadLetterQueueInfo> out = new ArrayList<>();
        for (String q : SOURCE_QUEUES.stream().sorted().toList()) {
            out.add(new DeadLetterQueueInfo(q, dlqName(q), browse(q, 10_000).size()));
        }
        return out;
    }

    public List<DeadLetter> browse(String sourceQueue, int max) {
        requireKnown(sourceQueue);
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            List<DeadLetter> out = new ArrayList<>();
            try (QueueBrowser browser = session.createBrowser(session.createQueue(fqqn(sourceQueue)))) {
                Enumeration<?> e = browser.getEnumeration();
                while (e.hasMoreElements() && out.size() < max) {
                    out.add(toView((Message) e.nextElement()));
                }
            }
            return out;
        } catch (InvalidDestinationException e) {
            return List.of(); // DLQs are auto-created on the first dead letter: none yet = empty
        } catch (JMSException e) {
            throw brokerError("browse", sourceQueue, e);
        }
    }

    /**
     * Moves dead letters back to the source queue. {@code messageId} null = all (up to max).
     * Only user properties (such as the _type header the converter needs) are copied; broker-internal
     * ones (_AMQ_*, delivery counts) are dropped so the replayed message starts with a clean slate
     * of delivery attempts.
     */
    public ReplayResult replay(String sourceQueue, String messageId, int max) {
        requireKnown(sourceQueue);
        List<String> ids = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue target = session.createQueue(sourceQueue);
            try (MessageConsumer consumer = session.createConsumer(
                         session.createQueue(fqqn(sourceQueue)), selector(messageId));
                 MessageProducer producer = session.createProducer(target)) {
                Message m;
                while (ids.size() < max && (m = consumer.receive(RECEIVE_TIMEOUT_MS)) != null) {
                    producer.send(copy(session, m));
                    ids.add(m.getJMSMessageID());
                }
                session.commit();
            } catch (JMSException | RuntimeException e) {
                session.rollback();
                throw e;
            }
        } catch (InvalidDestinationException e) {
            // no DLQ yet = nothing to replay
        } catch (JMSException e) {
            throw brokerError("replay", sourceQueue, e);
        }
        if (messageId != null && ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No dead letter " + messageId + " in " + dlqName(sourceQueue));
        }
        log.warn("Replayed {} dead letter(s) from {} to {}: {}", ids.size(), dlqName(sourceQueue), sourceQueue, ids);
        return new ReplayResult(sourceQueue, ids.size(), ids);
    }

    /** Permanently removes ONE dead letter (a message that can never succeed). */
    public void delete(String sourceQueue, String messageId) {
        requireKnown(sourceQueue);
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            try (MessageConsumer consumer = session.createConsumer(
                    session.createQueue(fqqn(sourceQueue)), selector(messageId))) {
                Message m = consumer.receive(RECEIVE_TIMEOUT_MS);
                if (m == null) {
                    session.rollback();
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No dead letter " + messageId + " in " + dlqName(sourceQueue));
                }
                session.commit();
                log.warn("Deleted dead letter {} from {}", messageId, dlqName(sourceQueue));
            }
        } catch (InvalidDestinationException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No dead letter " + messageId + " in " + dlqName(sourceQueue));
        } catch (JMSException e) {
            throw brokerError("delete", sourceQueue, e);
        }
    }

    public static String dlqName(String sourceQueue) {
        return MessagingConstants.DLQ_PREFIX + sourceQueue;
    }

    private static String fqqn(String sourceQueue) {
        return MessagingConstants.DLQ_ADDRESS + "::" + dlqName(sourceQueue);
    }

    private static String selector(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        if (!messageId.matches("[A-Za-z0-9:\\-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid messageId");
        }
        return "JMSMessageID = '" + messageId + "'";
    }

    private static Message copy(Session session, Message original) throws JMSException {
        String body = original instanceof TextMessage t ? t.getText() : original.getBody(String.class);
        TextMessage copy = session.createTextMessage(body);
        for (String name : Collections.list((Enumeration<String>) original.getPropertyNames())) {
            if (!name.startsWith("_AMQ") && !name.startsWith("JMSX")) {
                copy.setObjectProperty(name, original.getObjectProperty(name));
            }
        }
        return copy;
    }

    private static DeadLetter toView(Message m) throws JMSException {
        String body;
        try {
            body = m instanceof TextMessage t ? t.getText() : m.getBody(String.class);
        } catch (JMSException e) {
            body = "<non-text message: " + m.getClass().getSimpleName() + ">";
        }
        if (body != null && body.length() > MAX_BODY) body = body.substring(0, MAX_BODY) + "...";
        return new DeadLetter(m.getJMSMessageID(),
                m.getStringProperty(MessagingConstants.TYPE_HEADER),
                m.getStringProperty("_AMQ_ORIG_QUEUE"),
                m.getJMSTimestamp() > 0 ? Instant.ofEpochMilli(m.getJMSTimestamp()) : null,
                body);
    }

    private static void requireKnown(String queue) {
        if (!SOURCE_QUEUES.contains(queue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown queue '" + queue + "'. Allowed: " + SOURCE_QUEUES);
        }
    }

    private static ResponseStatusException brokerError(String op, String queue, JMSException e) {
        log.error("DLQ {} failed for {}", op, queue, e);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Broker error during " + op + ": " + e.getMessage());
    }
}
