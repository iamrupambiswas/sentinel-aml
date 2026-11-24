package com.sentinel.aml.detection_service.config;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.security.RouteRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sentinel.aml.common.security.SentinelSecurityConfig.*;
import static org.springframework.http.HttpMethod.*;

/** Who may call which detection-service endpoint (Phase 6). Composite roles: ADMIN > SENIOR_ANALYST > ANALYST, ADMIN > INGEST. */
@Configuration
public class SecurityRoutes {

    @Bean
    public RouteRules routeRules() {
        return auth -> auth
                .requestMatchers(GET, ApiRoutes.RULES + "/**").hasRole(ANALYST)
                .requestMatchers(PUT, ApiRoutes.RULES + "/*").hasRole(ADMIN)
                .requestMatchers(ApiRoutes.API_V1 + "/detection/**", ApiRoutes.OPS_DLQ + "/**").hasRole(ADMIN);
    }
}
