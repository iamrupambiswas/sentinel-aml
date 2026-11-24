package com.sentinel.aml.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * A service's own "which role may call which endpoint" rules. Each service declares one bean;
 * {@link SentinelSecurityConfig} plugs it into the shared filter chain. Anything not matched is denied.
 */
@FunctionalInterface
public interface RouteRules {

    void apply(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth);
}
