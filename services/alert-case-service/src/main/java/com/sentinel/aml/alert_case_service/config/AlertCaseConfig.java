package com.sentinel.aml.alert_case_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.common.messaging.SentinelMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

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

    /**
     * Security starters are already on the classpath for Phase 6 (Keycloak JWT + roles). Until then
     * every endpoint is open, like the other services; without this bean Spring would put HTTP Basic
     * with a random password in front of the whole API.
     */
    @Bean
    public SecurityFilterChain openUntilPhase6(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .build();
    }
}
