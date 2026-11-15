package com.sentinel.aml.detection_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.messaging.SentinelMessageConverters;
import jakarta.jms.ConnectionFactory;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
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

    /**
     * Listener factory for TOPICS (P13 rule hot-reload). Boot's default factory is point-to-point
     * (queues): a topic listener on it would silently never receive anything.
     */
    @Bean
    public DefaultJmsListenerContainerFactory topicListenerFactory(ConnectionFactory connectionFactory,
                                                                   DefaultJmsListenerContainerFactoryConfigurer configurer) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory); // converter, transacted session, etc.
        factory.setPubSubDomain(true);
        return factory;
    }

    /**
     * Queue template, declared explicitly. Boot only auto-creates a JmsTemplate when NO JmsOperations
     * bean exists, so defining topicJmsTemplate below would otherwise remove it - and every plain
     * {@code JmsTemplate} injection (TransactionConsumer -> sentinel.detection.hits) would silently get
     * the topic template and publish hits to a topic nobody reads.
     */
    @Bean
    @Primary
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setPubSubDomain(false);
        return template;
    }

    /** Sends to topics (sentinel.rules.changed). Inject with @Qualifier("topicJmsTemplate"). */
    @Bean
    public JmsTemplate topicJmsTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setPubSubDomain(true);
        return template;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
