package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

class TurCsrfCookieFilterTest {

    private final TurCsrfCookieFilter filter = new TurCsrfCookieFilter();

    @Test
    void shouldSetCsrfHeaderAndExposeItWhenExposeHeaderIsMissing() throws Exception {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-123");
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("token-123", response.getHeader("X-CSRF-TOKEN"));
        assertEquals("X-CSRF-TOKEN", response.getHeader("Access-Control-Expose-Headers"));
    }

    @Test
    void shouldAppendCsrfHeaderToExposeHeadersWhenDifferentHeaderAlreadyExists() throws Exception {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-456");
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Access-Control-Expose-Headers", "X-Trace-Id");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("X-Trace-Id, X-CSRF-TOKEN", response.getHeader("Access-Control-Expose-Headers"));
        assertEquals("token-456", response.getHeader("X-CSRF-TOKEN"));
    }

    @Test
    void shouldNotDuplicateCsrfHeaderInExposeHeaders() throws Exception {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-789");
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", csrfToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("Access-Control-Expose-Headers", "X-Trace-Id, X-CSRF-TOKEN");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("X-Trace-Id, X-CSRF-TOKEN", response.getHeader("Access-Control-Expose-Headers"));
        assertEquals("token-789", response.getHeader("X-CSRF-TOKEN"));
    }
}
