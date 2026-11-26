package com.sentinel.aml.gateway_service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Phase 7: rejects oversized request bodies with 413 at the edge, before they are streamed to a service.
 * A declared Content-Length is checked up front; a chunked body (no length) is counted while it is read
 * and cut off as soon as it passes the limit. CSV batch uploads get a larger limit than JSON calls.
 * Runs right after the request logger, before authentication, so a huge body is refused cheaply.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBody;
    private final long maxUpload;
    private final List<String> uploadPaths;

    public RequestSizeLimitFilter(@Value("${sentinel.request-limits.max-body:1MB}") DataSize maxBody,
                                  @Value("${sentinel.request-limits.max-upload:50MB}") DataSize maxUpload,
                                  @Value("${sentinel.request-limits.upload-paths:/api/v1/ingestion/batches}") List<String> uploadPaths) {
        this.maxBody = maxBody.toBytes();
        this.maxUpload = maxUpload.toBytes();
        this.uploadPaths = uploadPaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitFor(request.getRequestURI());
        if (request.getContentLengthLong() > limit) {
            reject(response, limit);
            return;
        }
        try {
            chain.doFilter(new LimitedRequest(request, limit), response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (!isTooLarge(e)) throw e;
            if (!response.isCommitted()) {
                response.reset();
                reject(response, limit);
            }
        }
    }

    long limitFor(String uri) {
        return uploadPaths.stream().anyMatch(uri::startsWith) ? maxUpload : maxBody;
    }

    private static void reject(HttpServletResponse response, long limit) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"Request body too large: the limit for this path is "
                + DataSize.ofBytes(limit).toKilobytes() + " KB.\"}");
    }

    /** The proxy may wrap our exception; look through the cause chain. */
    private static boolean isTooLarge(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BodyTooLargeException) return true;
        }
        return false;
    }

    static final class BodyTooLargeException extends IOException {
        BodyTooLargeException() {
            super("Request body exceeds the configured limit");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getInputStream(), limit);
            }
            return stream;
        }
    }

    private static final class CountingStream extends ServletInputStream {
        private final ServletInputStream in;
        private final long limit;
        private long count;

        CountingStream(ServletInputStream in, long limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != -1) add(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = in.read(buf, off, len);
            if (n > 0) add(n);
            return n;
        }

        private void add(long n) throws IOException {
            count += n;
            if (count > limit) throw new BodyTooLargeException();
        }

        @Override public boolean isFinished() { return in.isFinished(); }
        @Override public boolean isReady() { return in.isReady(); }
        @Override public void setReadListener(ReadListener listener) { in.setReadListener(listener); }
    }
}
