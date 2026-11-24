package com.sentinel.aml.alert_case_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.messaging.SentinelMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MessageConverter;

import java.time.Clock;

@Configuration
@EnableJms
public class AlertCaseConfig {

    /** Same JSON format and type ids as the producer (detection-service). */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        return SentinelMessageConverters.jackson(objectMapper);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
