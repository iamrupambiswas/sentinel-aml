package com.sentinel.aml.alert_case_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.messaging.SentinelMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.jms.ConnectionFactory;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jms.support.converter.MessageConverter;

import java.time.Clock;

@Configuration
@EnableJms
@EnableScheduling // SSE heartbeat (live/AlertStreamService)
public class AlertCaseConfig {

    /** Same JSON format and type ids as the producer (detection-service). */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        return SentinelMessageConverters.jackson(objectMapper);
    }

    /**
     * Listener factory for TOPICS (Phase 7 live alerts). Boot's default factory is point-to-point:
     * a topic listener on it would silently never receive anything.
     */
    @Bean
    public DefaultJmsListenerContainerFactory topicListenerFactory(ConnectionFactory connectionFactory,
                                                                   DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPubSubDomain(true);
        return factory;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
