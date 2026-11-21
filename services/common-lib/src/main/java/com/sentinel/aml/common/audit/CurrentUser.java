package com.sentinel.aml.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The Keycloak username of the caller (set by SentinelSecurityConfig), or "system" outside a user request. */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private CurrentUser() {}

    public static String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getName() != null ? auth.getName() : SYSTEM;
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
