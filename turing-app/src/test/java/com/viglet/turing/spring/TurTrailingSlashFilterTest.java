package com.viglet.turing.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

class TurTrailingSlashFilterTest {

    private final TurTrailingSlashFilter filter = new TurTrailingSlashFilter();

    @Test
    void shouldRemoveTrailingSlashForApiPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sn/search/");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        HttpServletRequest wrappedRequest = (HttpServletRequest) chain.request;
        assertEquals("/api/sn/search", wrappedRequest.getRequestURI());
        assertEquals("http://localhost:8080/api/sn/search", wrappedRequest.getRequestURL().toString());
        assertSame(response, chain.response);
    }

    @Test
    void shouldKeepRequestWhenPathIsNotApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/content/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.request);
        assertSame(response, chain.response);
    }

    @Test
    void shouldKeepRequestWhenApiPathHasNoTrailingSlash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sn/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.request);
        assertSame(response, chain.response);
    }

    private static class CapturingFilterChain implements FilterChain {
        private ServletRequest request;
        private ServletResponse response;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.request = request;
            this.response = response;
        }
    }
}