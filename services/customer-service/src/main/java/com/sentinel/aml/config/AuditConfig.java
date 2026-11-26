package com.sentinel.aml.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.audit.AuditPublisher;
import com.sentinel.aml.common.messaging.SentinelMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

/** Phase 7b: customer-service records who onboarded, re-rated or revealed what, in the shared audit log. */
@Configuration
public class AuditConfig {

    /** Same JSON format and type ids as every other service. Boot wires it into its JmsTemplate. */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        return SentinelMessageConverters.jackson(objectMapper);
    }

    @Bean
    public AuditPublisher auditPublisher(JmsTemplate jmsTemplate) {
        return new AuditPublisher(jmsTemplate, "customer-service");
    }
}
