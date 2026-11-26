package com.sentinel.aml.gateway_service.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(
            DataSize.ofBytes(10), DataSize.ofBytes(100), List.of("/api/v1/ingestion/batches"));

    private static MockHttpServletRequest post(String uri, byte[] body, boolean declareLength) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return declareLength ? body.length : -1; // -1 = chunked, length unknown
            }
        };
        req.setContent(body);
        return req;
    }

    /** Downstream stand-in: reads the whole body, like the proxy does. */
    private static final jakarta.servlet.FilterChain READ_ALL = (rq, rs) -> {
        StreamUtils.copyToByteArray(rq.getInputStream());
        ((jakarta.servlet.http.HttpServletResponse) rs).setStatus(200);
    };

    @Test
    void declaredLengthOverLimitIs413WithoutCallingDownstream() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/alerts", new byte[11], true), res, (rq, rs) -> {
            throw new AssertionError("must not reach downstream");
        });
        assertThat(res.getStatus()).isEqualTo(413);
        assertThat(res.getContentAsString()).contains("too large");
    }

    @Test
    void chunkedBodyIsCountedAndCutOff() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/alerts", new byte[50], false), res, READ_ALL);
        assertThat(res.getStatus()).isEqualTo(413);
    }

    @Test
    void bodyWithinLimitPasses() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/alerts", new byte[10], false), res, READ_ALL);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void uploadPathGetsTheLargerLimit() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/ingestion/batches", new byte[60], true), res, READ_ALL);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(filter.limitFor("/api/v1/ingestion/batches/abc")).isEqualTo(100);
    }
}
