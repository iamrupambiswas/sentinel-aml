package com.sentinel.aml.common.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.UUID;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * PII and secrets never reach the logs (Phase 7b; ADR-002 flagged the raw national ID on registration).
     * Matches record/bean toString output ("idNumber=PAN-ABCD1234E") and JSON ("\"idNumber\":\"...\"").
     */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(\"?\\b(?:idNumber|nationalId|password|secret|clientSecret|token|accessToken|refreshToken|authorization)\"?\\s*[=:]\\s*\"?)([^,\"\\])}]*)");

    static String redact(String text) {
        return text == null ? null : SENSITIVE.matcher(text).replaceAll("$1***");
    }

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {}

    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        // 1. Reuse the gateway's Trace ID (X-Trace-Id) so both log lines match; otherwise generate one
        String incomingTraceId = request.getHeader(TRACE_HEADER);
        String traceId = (incomingTraceId != null && !incomingTraceId.isBlank())
                ? incomingTraceId
                : UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);

        // 2. Extract HTTP Request Details
        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();

        // 3. Extract Class and Method details (cleaner format)
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("▶ [REQ] [{}] {} {} | Executing: {}.{}() | Payload: {}",
                traceId, httpMethod, uri, className, methodName, redact(Arrays.toString(args)));

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsedTime = System.currentTimeMillis() - startTime;

            // Extract actual HTTP Status if returning ResponseEntity
            String status = "SUCCESS";
            if (result instanceof ResponseEntity<?> responseEntity) {
                status = responseEntity.getStatusCode().toString();
            }

            log.info("◀ [RES] [{}] {} {} | Status: {} | Time: {}ms",
                    traceId, httpMethod, uri, status, elapsedTime);

            return result;
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.error("✖ [ERR] [{}] {} {} | Failed: {} | Time: {}ms | Error: {}",
                    traceId, httpMethod, uri, e.getClass().getSimpleName(), elapsedTime, e.getMessage());
            throw e;
        } finally {
            // Clean up MDC thread-local to prevent memory leaks
            MDC.clear();
        }
    }
}