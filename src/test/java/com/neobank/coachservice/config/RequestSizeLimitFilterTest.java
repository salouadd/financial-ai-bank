package com.neobank.coachservice.config;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {

    @Test
    void rejectsPayloadAboveConfiguredLimit() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("Payload Too Large");
    }

    @Test
    void passesPayloadAtConfiguredLimitToController() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("12345678".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ServletInputStream body = chain.getRequest().getInputStream();
        assertThat(body.readAllBytes()).hasSize(8);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
