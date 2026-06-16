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

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Forces the deferred {@link CsrfToken} to render (so the
 * {@code CookieCsrfTokenRepository} writes the {@code XSRF-TOKEN} cookie) and
 * echoes the token in the response header, adding the header name to
 * {@code Access-Control-Expose-Headers} so cross-origin SPAs can read it.
 *
 * <p>Lifted verbatim from Turing's {@code TurCsrfCookieFilter} into
 * {@code viglet-core} (Block Q / T369); it carries no product knowledge. Pair it
 * with a {@code CookieCsrfTokenRepository} + {@code CsrfTokenRequestAttributeHandler}
 * in the product's {@code SecurityFilterChain}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletCsrfCookieFilter extends OncePerRequestFilter {

    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        // Render the token value to a cookie by causing the deferred token to be loaded
        String token = csrfToken.getToken();
        response.setHeader(csrfToken.getHeaderName(), token);

        String exposeHeaders = response.getHeader(EXPOSE_HEADERS);
        if (!StringUtils.hasText(exposeHeaders)) {
            response.setHeader(EXPOSE_HEADERS, csrfToken.getHeaderName());
        } else if (!exposeHeaders.contains(csrfToken.getHeaderName())) {
            response.setHeader(EXPOSE_HEADERS, exposeHeaders + ", " + csrfToken.getHeaderName());
        }

        filterChain.doFilter(request, response);
    }
}
