package com.sentinel.aml.gateway_service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Logs every request that passes through the gateway, in the same ▶ [REQ] / ◀ [RES] / ✖ [ERR]
 * format the downstream services use (common-lib LoggingAspect).
 *
 * The gateway has no @RestController, so the aspect never fires here; a servlet filter sees every
 * proxied call instead. The trace ID is forwarded downstream as {@value #TRACE_HEADER} and returned
 * to the client, so one request can be followed from the gateway into the service that handled it.
 *
 * Bodies are deliberately not logged here: downstream services already log payloads, and buffering
 * them in the gateway would mean holding every request/response body in memory.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TRACE_ID = "traceId";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health checks and actuator polling would drown out real traffic.
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(TRACE_HEADER);
        String traceId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        String method = request.getMethod();
        String uri = request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();

        log.info("▶ [REQ] [{}] {} {} | Client: {}", traceId, method, uri, clientIp(request));

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(new TraceHeaderRequest(request, traceId), response);
            log.info("◀ [RES] [{}] {} {} | Status: {} | Time: {}ms",
                    traceId, method, uri, response.getStatus(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("✖ [ERR] [{}] {} {} | Failed: {} | Time: {}ms | Error: {}",
                    traceId, method, uri, e.getClass().getSimpleName(), System.currentTimeMillis() - start,
                    e.getMessage());
            throw e;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    /** Adds the trace header to the request so the gateway's proxy copies it to the downstream call. */
    private static final class TraceHeaderRequest extends HttpServletRequestWrapper {
        private final String traceId;

        TraceHeaderRequest(HttpServletRequest request, String traceId) {
            super(request);
            this.traceId = traceId;
        }

        @Override
        public String getHeader(String name) {
            return TRACE_HEADER.equalsIgnoreCase(name) ? traceId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return TRACE_HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(traceId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String n = original.nextElement();
                if (!TRACE_HEADER.equalsIgnoreCase(n)) {
                    names.add(n);
                }
            }
            names.add(TRACE_HEADER);
            return Collections.enumeration(names);
        }
    }
}
