package com.sentinel.aml.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.constants.MessagingConstants;
import com.sentinel.aml.common.event.RuleHitsDetectedEvent;
import com.sentinel.aml.common.event.RulesChangedEvent;
import com.sentinel.aml.common.event.TransactionIngestedEvent;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.Map;

/**
 * The one JSON converter every messaging service uses, so producer and consumer agree on
 * format and type ids. Pass in Spring's ObjectMapper so java.time types (Instant) serialize.
 */
public final class SentinelMessageConverters {

    private SentinelMessageConverters() {}

    public static MessageConverter jackson(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName(MessagingConstants.TYPE_HEADER);
        converter.setTypeIdMappings(Map.of(
                MessagingConstants.TYPE_TRANSACTION_INGESTED, TransactionIngestedEvent.class,
                MessagingConstants.TYPE_RULE_HITS_DETECTED, RuleHitsDetectedEvent.class,
                MessagingConstants.TYPE_RULES_CHANGED, RulesChangedEvent.class
        ));
        return converter;
    }
}
