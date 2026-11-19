package com.sentinel.aml.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelSecurityConfigTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("uuid-123");
    }

    @Test
    void mapsRealmRolesAndUsername() {
        Jwt jwt = token()
                .claim("preferred_username", "senior1")
                .claim("realm_access", Map.of("roles", List.of("SENIOR_ANALYST", "ANALYST")))
                .build();

        var auth = SentinelSecurityConfig.keycloakJwtConverter().convert(jwt);

        assertThat(auth.getName()).isEqualTo("senior1");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SENIOR_ANALYST", "ROLE_ANALYST");
    }

    @Test
    void tokenWithoutRealmRolesHasNoAuthoritiesAndFallsBackToSubject() {
        var auth = SentinelSecurityConfig.keycloakJwtConverter().convert(token().build());

        assertThat(auth.getName()).isEqualTo("uuid-123");
        assertThat(auth.getAuthorities()).isEmpty();
    }
}
