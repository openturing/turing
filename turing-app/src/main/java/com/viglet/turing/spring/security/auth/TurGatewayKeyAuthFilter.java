/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.auth;

import java.io.IOException;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.genai.gateway.TurGatewayContext;
import com.viglet.turing.genai.gateway.TurGatewayKeyService;
import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.properties.TurGatewayProperty;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * T741 / §XLIX — authenticates the Governed LLM Gateway's {@code /v1/*} surface
 * with a virtual key presented as {@code Authorization: Bearer sk-turing-...},
 * parallel to the existing {@code Key}-header dev-token filter
 * ({@link TurAuthTokenHeaderFilter}).
 *
 * <p>Only runs on {@code /v1/*}. When the gateway is off it is a no-op (the
 * controller is not even registered). When on and
 * {@code turing.gateway.require-virtual-key=true} (the default), a missing or
 * invalid key short-circuits with an OpenAI-shaped {@code 401}. A valid key is
 * bound into the {@link SecurityContextHolder} (principal = key name, authority
 * {@code ROLE_GATEWAY}) and into {@link TurGatewayContext} for the request, then
 * cleared in {@code finally}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurGatewayKeyAuthFilter extends OncePerRequestFilter {

    private static final String GATEWAY_PATH_PREFIX = "/v1/";
    private static final String BEARER = "Bearer ";
    public static final String ROLE_GATEWAY = "ROLE_GATEWAY";
    private static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"message\":\"Missing or invalid virtual key. Provide "
                    + "'Authorization: Bearer sk-turing-...'.\",\"type\":\"invalid_request_error\","
                    + "\"code\":\"invalid_api_key\"}}";

    private final TurGatewayKeyService gatewayKeyService;
    private final TurGatewayProperty gatewayProperty;

    public TurGatewayKeyAuthFilter(TurGatewayKeyService gatewayKeyService,
            TurGatewayProperty gatewayProperty) {
        this.gatewayKeyService = gatewayKeyService;
        this.gatewayProperty = gatewayProperty;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the gateway surface; and only when the gateway is enabled.
        return !gatewayProperty.isEnabled()
                || !request.getRequestURI().startsWith(GATEWAY_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {
        String rawKey = extractBearer(request);
        TurGatewayKey key = rawKey == null ? null : gatewayKeyService.authenticate(rawKey).orElse(null);

        if (key == null) {
            if (gatewayProperty.isRequireVirtualKey()) {
                writeUnauthorized(response);
                return;
            }
            // Open mode (trusted network): proceed with no gateway identity.
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        "gwkey:" + key.getId(), null,
                        List.of(new SimpleGrantedAuthority(ROLE_GATEWAY)));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            TurGatewayContext.set(key);
            filterChain.doFilter(request, response);
        } finally {
            TurGatewayContext.clear();
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
