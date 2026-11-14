package com.sentinel.aml.detection_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.messaging.SentinelMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MessageConverter;

import java.time.Clock;

@Configuration
@EnableJms
public class JmsConfig {

    /** Shared converter: same type ids and JSON format as ingestion-service. Boot wires it into listeners and JmsTemplate. */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        return SentinelMessageConverters.jackson(objectMapper);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
