/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class VigletCsrfCookieFilterTest {

    private static final String HEADER = "X-CSRF-TOKEN";
    private static final String EXPOSE = "Access-Control-Expose-Headers";

    private final VigletCsrfCookieFilter filter = new VigletCsrfCookieFilter();

    private CsrfToken token() {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-123");
        when(csrfToken.getHeaderName()).thenReturn(HEADER);
        return csrfToken;
    }

    @Test
    void echoesTokenAndExposesHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        CsrfToken csrfToken = token();
        when(request.getAttribute("_csrf")).thenReturn(csrfToken);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(HEADER, "token-123");
        verify(response).setHeader(EXPOSE, HEADER);
        verify(chain).doFilter(request, response);
    }

    @Test
    void appendsToExistingExposeHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        CsrfToken csrfToken = token();
        when(request.getAttribute("_csrf")).thenReturn(csrfToken);
        when(response.getHeader(EXPOSE)).thenReturn("Location");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(EXPOSE, "Location, " + HEADER);
    }
}
