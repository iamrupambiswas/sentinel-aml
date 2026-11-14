package com.sentinel.aml.detection_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJms
public class JmsConfig {

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();

        // Serialize messages as JSON text rather than binary bytes
        converter.setTargetType(MessageType.TEXT);

        // This property allows the consumer to know which class to deserialize into
        converter.setTypeIdPropertyName("_type");

        // Map the type identifiers to the local DTO classes to prevent package-mismatch errors
        Map<String, Class<?>> typeIdMappings = new HashMap<>();
        typeIdMappings.put("TransactionEvent", com.sentinel.aml.detection_service.dto.TransactionEvent.class);
        typeIdMappings.put("AlertGeneratedEvent", com.sentinel.aml.detection_service.dto.AlertGeneratedEvent.class);
        converter.setTypeIdMappings(typeIdMappings);

        return converter;
    }
}