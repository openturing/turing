/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TurMcpApiKeyAuthenticationFilterTest {

    @Mock private TurDevTokenRepository repository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private TurMcpApiKeyAuthenticationFilter filter() {
        return new TurMcpApiKeyAuthenticationFilter(repository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesReadOnlyOnValidKeyHeader() throws Exception {
        TurDevToken token = new TurDevToken();
        token.setId("tok-1");
        when(request.getHeader("Key")).thenReturn("secret-key");
        when(repository.findByToken("secret-key")).thenReturn(Optional.of(token));

        filter().doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(auth.getName()).isEqualTo("mcp-apikey:tok-1");
        assertThat(auth.getAuthorities()).extracting(Object::toString)
                .containsExactly("SCOPE_mcp:read")
                .doesNotContain("SCOPE_mcp:write");
        verify(chain).doFilter(request, response);
    }

    @Test
    void apiKeyQueryParamIsIgnored() throws Exception {
        // T646 / §XXXVII.8 — the `?apiKey=` query param no longer authenticates
        // (header only); a key supplied only in the URL leaves the request anon.
        when(request.getHeader("Key")).thenReturn(null);

        filter().doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledTokenLeavesRequestUnauthenticated() throws Exception {
        // T646 / §XXXVII.8 — a found-but-unusable token does not authenticate.
        TurDevToken disabled = new TurDevToken();
        disabled.setId("tok-x");
        disabled.setEnabled(false);
        when(request.getHeader("Key")).thenReturn("stale");
        when(repository.findByToken("stale")).thenReturn(Optional.of(disabled));

        filter().doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unknownKeyLeavesRequestUnauthenticated() throws Exception {
        when(request.getHeader("Key")).thenReturn("bogus");
        when(repository.findByToken("bogus")).thenReturn(Optional.empty());

        filter().doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void noKeyIsANoOp() throws Exception {
        when(request.getHeader("Key")).thenReturn(null);

        filter().doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(repository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotOverrideAnExistingOAuthAuthentication() throws Exception {
        // A valid OAuth bearer already authenticated this request — the API-key
        // filter must not clobber it (and must not even look up a key).
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("oauth-user", null, "SCOPE_mcp:write"));
        lenient().when(request.getHeader("Key")).thenReturn("secret-key");

        filter().doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("oauth-user");
        verify(repository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }
}
