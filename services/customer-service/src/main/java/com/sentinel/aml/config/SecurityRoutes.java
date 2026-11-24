package com.sentinel.aml.config;

import com.sentinel.aml.common.constants.ApiRoutes;
import com.sentinel.aml.common.security.RouteRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sentinel.aml.common.security.SentinelSecurityConfig.*;
import static org.springframework.http.HttpMethod.*;

/** Who may call which customer-service endpoint (Phase 6). Composite roles: ADMIN > SENIOR_ANALYST > ANALYST, ADMIN > INGEST. */
@Configuration
public class SecurityRoutes {

    @Bean
    public RouteRules routeRules() {
        return auth -> auth
                .requestMatchers(GET, ApiRoutes.CUSTOMERS + "/**", ApiRoutes.ACCOUNTS + "/**").hasRole(ANALYST)
                .requestMatchers(PUT, ApiRoutes.CUSTOMERS + "/*/risk-rating", ApiRoutes.ACCOUNTS + "/*/risk-rating")
                        .hasRole(SENIOR_ANALYST)
                .requestMatchers(POST, ApiRoutes.CUSTOMERS, ApiRoutes.CUSTOMERS + "/*/accounts").hasRole(ADMIN)
                .requestMatchers(ApiRoutes.ADMIN + "/**").hasRole(ADMIN);
    }
}
