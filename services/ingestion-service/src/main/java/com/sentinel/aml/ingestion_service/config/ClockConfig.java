package com.sentinel.aml.ingestion_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    /** Injected everywhere instead of calling now() directly, so tests can freeze time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
