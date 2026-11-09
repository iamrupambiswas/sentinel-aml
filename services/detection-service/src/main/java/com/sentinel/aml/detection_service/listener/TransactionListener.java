package com.sentinel.aml.detection_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.detection_service.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionListener {

    // Spring Boot automatically provides a pre-configured ObjectMapper
    private final ObjectMapper objectMapper;

    @JmsListener(destination = MessagingConstants.TRANSACTION_QUEUE)
    public void consumeTransaction(Map<String, Object> rawEvent) {

        // 1. Convert the generic Map into our strict Java Record
        TransactionEvent event = objectMapper.convertValue(rawEvent, TransactionEvent.class);

        // 2. Log it to prove it parsed correctly!
        log.info("------------- NEW TRANSACTION RECEIVED -------------");
        log.info("Transaction ID : {}", event.transactionId());
        log.info("Timestamp      : {}", event.timestamp());
        log.info("Amount         : ${}", event.getAmount());
        log.info("Full Payload   : {}", event.payload());
        log.info("--------------------------------------------------");

        // Phase 4: Pass this event into our AML Rule Engine
    }
}