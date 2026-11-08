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
import java.util.UUID;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final String TRACE_ID = "traceId";

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {}

    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. Generate a unique short Trace ID for the request
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);

        // 2. Extract HTTP Request Details
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();

        // 3. Extract Class and Method details (cleaner format)
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("▶ [REQ] [{}] {} {} | Executing: {}.{}() | Payload: {}",
                traceId, httpMethod, uri, className, methodName, Arrays.toString(args));

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