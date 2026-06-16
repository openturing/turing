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
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JSON {@code 401 Unauthorized} entry-point for REST/SPA backends: instead of
 * the servlet container's default HTML page (or a browser Basic-auth prompt on
 * every API call), it writes {@code {"error":"Unauthorized"}}.
 *
 * <p>Lifted from Turing's {@code TurAuthenticationEntryPoint} into
 * {@code viglet-core} (Block Q / T369). The one product-specific bit — a
 * {@code WWW-Authenticate: Basic realm="..."} header on certain paths so native
 * clients (e.g. Git over HTTP) still get a credential prompt — is generalized
 * into a list of {@link BasicRealmRule}s the consumer supplies. With no rules
 * (the default) the response is a plain JSON 401 with no challenge header.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHORIZED_BODY = "{\"error\":\"Unauthorized\"}";

    /**
     * Emits {@code WWW-Authenticate: Basic realm="<realm>"} when the request URI
     * starts with {@code pathPrefix}, so native HTTP clients on that surface get
     * a Basic-auth credential prompt.
     */
    public record BasicRealmRule(String pathPrefix, String realm) {
    }

    private final List<BasicRealmRule> basicRealmRules;

    /** No challenge header on any path — plain JSON 401 everywhere. */
    public VigletAuthenticationEntryPoint() {
        this(List.of());
    }

    public VigletAuthenticationEntryPoint(List<BasicRealmRule> basicRealmRules) {
        this.basicRealmRules = basicRealmRules != null ? List.copyOf(basicRealmRules) : List.of();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String uri = request.getRequestURI();
        if (uri != null) {
            for (BasicRealmRule rule : basicRealmRules) {
                if (uri.startsWith(rule.pathPrefix())) {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"" + rule.realm() + "\"");
                    break;
                }
            }
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
