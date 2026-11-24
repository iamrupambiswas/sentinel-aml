package com.sentinel.aml.alert_case_service.config;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.security.RouteRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sentinel.aml.common.security.SentinelSecurityConfig.*;
import static org.springframework.http.HttpMethod.*;

/** Who may call which alert-case-service endpoint (Phase 6). Composite roles: ADMIN > SENIOR_ANALYST > ANALYST, ADMIN > INGEST. */
@Configuration
public class SecurityRoutes {

    @Bean
    public RouteRules routeRules() {
        return auth -> auth
                .requestMatchers(GET, ApiRoutes.ALERTS + "/**").hasRole(ANALYST)
                .requestMatchers(PATCH, ApiRoutes.ALERTS + "/*/status").hasRole(ANALYST);
    }
}
