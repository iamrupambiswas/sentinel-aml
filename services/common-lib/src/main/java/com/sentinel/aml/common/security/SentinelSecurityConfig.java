package com.sentinel.aml.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Shared security for every service (Phase 6): stateless OAuth2 resource server validating Keycloak JWTs.
 * The gateway checks the token too; this is the second line, so calling a service port directly still
 * needs a valid token. Issuer: {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
 *
 * Roles come from Keycloak's {@code realm_access.roles}. Role inheritance (ADMIN > SENIOR_ANALYST > ANALYST)
 * is done with Keycloak composite roles, so the token already lists every effective role.
 */
@Configuration
public class SentinelSecurityConfig {

    public static final String ANALYST = "ANALYST";
    public static final String SENIOR_ANALYST = "SENIOR_ANALYST";
    public static final String ADMIN = "ADMIN";
    public static final String INGEST = "INGEST";

    @Bean
    public SecurityFilterChain sentinelFilterChain(HttpSecurity http, RouteRules routeRules) throws Exception {
        return http
                // Bearer tokens, no cookies: CSRF does not apply and there is no session to keep
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs/**").permitAll();
                    routeRules.apply(auth);
                    // Default deny: a new endpoint without a rule is closed until someone decides who may call it
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(keycloakJwtConverter())))
                .build();
    }

    /** Principal name = Keycloak username; authorities = ROLE_ + each realm role. */
    static Converter<Jwt, AbstractAuthenticationToken> keycloakJwtConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, realmRoles(jwt), username(jwt));
    }

    static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }

    private static String username(Jwt jwt) {
        String name = jwt.getClaimAsString("preferred_username");
        return name != null ? name : jwt.getSubject();
    }
}
