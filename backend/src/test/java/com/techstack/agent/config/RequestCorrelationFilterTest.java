package com.techstack.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void usesValidatedProxyRequestIdForCrossServiceCorrelation() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        request.addHeader(RequestCorrelationFilter.PROXY_HEADER_NAME, requestId.toUpperCase());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER_NAME)).isEqualTo(requestId);
    }

    @Test
    void replacesUntrustedOrMalformedRequestIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        request.addHeader(RequestCorrelationFilter.PROXY_HEADER_NAME, "forged\nvalue");
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "browser-controlled");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(RequestCorrelationFilter.HEADER_NAME);
        assertThat(requestId).isNotEqualTo("browser-controlled").isNotEqualTo("forged\nvalue");
        assertThat(UUID.fromString(requestId)).isNotNull();
    }
}
