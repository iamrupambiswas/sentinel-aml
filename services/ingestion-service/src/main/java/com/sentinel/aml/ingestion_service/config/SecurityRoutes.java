package com.sentinel.aml.ingestion_service.config;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.security.RouteRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sentinel.aml.common.security.SentinelSecurityConfig.*;
import static org.springframework.http.HttpMethod.*;

/** Who may call which ingestion-service endpoint (Phase 6). Composite roles: ADMIN > SENIOR_ANALYST > ANALYST, ADMIN > INGEST. */
@Configuration
public class SecurityRoutes {

    @Bean
    public RouteRules routeRules() {
        return auth -> auth
                .requestMatchers(POST, ApiRoutes.TRANSACTIONS, ApiRoutes.TRANSACTIONS + "/ingest", ApiRoutes.INGESTION_BATCHES)
                        .hasRole(INGEST)
                .requestMatchers(GET, ApiRoutes.INGESTION_BATCHES + "/*").hasRole(ANALYST);
    }
}
