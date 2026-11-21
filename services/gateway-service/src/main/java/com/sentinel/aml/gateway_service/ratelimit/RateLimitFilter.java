package com.sentinel.aml.gateway_service.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Phase 7: limits each caller to a burst of {@code capacity} requests, refilled at a steady rate.
 * Registered without an order, so it runs AFTER Spring Security: unauthenticated requests are already
 * rejected (401) and the caller is known. The key is the JWT subject (one bucket per user or machine
 * client), so one noisy client cannot starve the others.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RedisRateLimiter limiter;

    public RateLimitFilter(RedisRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RedisRateLimiter.Decision decision;
        try {
            decision = limiter.tryConsume(caller(request));
        } catch (RuntimeException e) {
            // Fail open: Redis being down must not take the whole API down. Logged so it is visible.
            log.warn("Rate limiter unavailable, letting request through: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", Long.toString(limiter.capacity()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded: at most "
                + limiter.capacity() + " requests in a burst. Retry after " + retryAfterSeconds + " s.\"}");
    }

    private static String caller(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return "sub:" + jwt.getToken().getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
