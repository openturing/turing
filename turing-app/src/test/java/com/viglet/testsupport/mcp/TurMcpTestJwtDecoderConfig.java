/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.testsupport.mcp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Test-only {@link JwtDecoder} that stands in for a real Keycloak so the T246
 * MCP resource-server gate can be exercised without a running IdP.
 *
 * <p>Lives under {@code com.viglet.testsupport.*} (outside the
 * {@code com.viglet.turing} component scan) so it is only active when a test
 * explicitly {@code @Import}s it — it never leaks into other {@code @SpringBootTest}s.
 *
 * <p>Accepts a single canned token, {@link #VALID_TOKEN}, decoding it to a JWT
 * carrying a Keycloak {@code realm_access.roles} claim and an OAuth2 scope; any
 * other token value is rejected, which the resource server turns into HTTP 401.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@TestConfiguration
public class TurMcpTestJwtDecoderConfig {

    public static final String VALID_TOKEN = "mcp-it-valid-token";

    @Bean
    JwtDecoder mcpTestJwtDecoder() {
        return token -> {
            if (!VALID_TOKEN.equals(token)) {
                throw new JwtException("invalid test token");
            }
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("mcp-it-user")
                    .issuedAt(now)
                    .expiresAt(now.plus(1, ChronoUnit.HOURS))
                    .claim("scope", "mcp:read")
                    .claim("realm_access", Map.of("roles", List.of("USER")))
                    .build();
        };
    }
}
