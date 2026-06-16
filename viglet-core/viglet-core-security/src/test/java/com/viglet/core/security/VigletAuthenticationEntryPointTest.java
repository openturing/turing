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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import com.viglet.core.security.VigletAuthenticationEntryPoint.BasicRealmRule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class VigletAuthenticationEntryPointTest {

    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String GIT_PREFIX = "/git/";
    private static final String GIT_REALM = "Turing Git";
    private static final AuthenticationException AUTH_EX = new BadCredentialsException("Bad credentials");

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final StringWriter body = new StringWriter();

    private void stubWriter() throws java.io.IOException {
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    @Test
    void writesJsonUnauthorizedBody() throws Exception {
        stubWriter();

        new VigletAuthenticationEntryPoint().commence(request, response, AUTH_EX);

        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.toString()).isEqualTo("{\"error\":\"Unauthorized\"}");
    }

    @Test
    void noChallengeHeaderWithoutRules() throws Exception {
        stubWriter();
        when(request.getRequestURI()).thenReturn(GIT_PREFIX + "repo.git/info/refs");

        new VigletAuthenticationEntryPoint().commence(request, response, AUTH_EX);

        verify(response, never()).setHeader(eq(WWW_AUTHENTICATE), anyString());
    }

    @Test
    void setsBasicRealmChallengeWhenPathMatchesRule() throws Exception {
        stubWriter();
        when(request.getRequestURI()).thenReturn(GIT_PREFIX + "repo.git/info/refs");

        new VigletAuthenticationEntryPoint(List.of(new BasicRealmRule(GIT_PREFIX, GIT_REALM)))
                .commence(request, response, AUTH_EX);

        verify(response).setHeader(WWW_AUTHENTICATE, "Basic realm=\"" + GIT_REALM + "\"");
    }

    @Test
    void doesNotSetChallengeForNonMatchingPath() throws Exception {
        stubWriter();
        when(request.getRequestURI()).thenReturn("/api/sn/foo");

        new VigletAuthenticationEntryPoint(List.of(new BasicRealmRule(GIT_PREFIX, GIT_REALM)))
                .commence(request, response, AUTH_EX);

        verify(response, never()).setHeader(eq(WWW_AUTHENTICATE), anyString());
    }

    @Test
    void rulesListIsDefensivelyCopied() throws Exception {
        var rules = new java.util.ArrayList<BasicRealmRule>();
        rules.add(new BasicRealmRule(GIT_PREFIX, GIT_REALM));
        var entryPoint = new VigletAuthenticationEntryPoint(rules);
        rules.clear();
        stubWriter();
        when(request.getRequestURI()).thenReturn(GIT_PREFIX + "x");

        entryPoint.commence(request, response, AUTH_EX);

        verify(response).setHeader(WWW_AUTHENTICATE, "Basic realm=\"" + GIT_REALM + "\"");
    }
}
