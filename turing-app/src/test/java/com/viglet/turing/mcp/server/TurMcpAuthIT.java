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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.testsupport.mcp.TurMcpTestJwtDecoderConfig;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Integration test for T246 — the OAuth 2.1 resource-server gate on {@code /mcp}.
 *
 * <p>Boots the backend with the MCP server enabled, {@code require-auth=true}, and
 * {@code loopback-only=false} (so the 401 is unambiguously from missing auth, not
 * the loopback gate). A test-only {@link TurMcpTestJwtDecoderConfig} stands in for
 * Keycloak, so no IdP is needed.
 *
 * <p>Asserts the trust boundary: a request with no bearer token is rejected with
 * HTTP 401, while the same request carrying the canned valid token is accepted
 * (not 401) — the {@code JwtDecoder} validated it and the resource server let it
 * through.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jmx.enabled=true",
                "spring.ai.mcp.server.enabled=true",
                "turing.mcp-server.require-auth=true",
                "turing.mcp-server.loopback-only=false"
        })
@Import(TurMcpTestJwtDecoderConfig.class)
class TurMcpAuthIT extends AbstractTuringSpringIT {

    private static final String INITIALIZE_BODY = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{\
            "protocolVersion":"2025-03-26","capabilities":{},\
            "clientInfo":{"name":"it","version":"1.0"}}}""";

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void uniqueJmxObjectNames(DynamicPropertyRegistry registry) {
        registry.add("spring.jmx.unique-names", () -> "true");
    }

    private HttpRequest.Builder mcpRequestBuilder() {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
    }

    @Test
    void mcpEndpoint_rejectsRequestWithoutBearerToken() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                mcpRequestBuilder().POST(BodyPublishers.ofString(INITIALIZE_BODY)).build(),
                BodyHandlers.ofString());
        assertEquals(401, response.statusCode(),
                () -> "expected 401 for an unauthenticated /mcp request, got " + response.statusCode());
    }

    @Test
    void mcpEndpoint_acceptsValidBearerToken() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                mcpRequestBuilder()
                        .header("Authorization", "Bearer " + TurMcpTestJwtDecoderConfig.VALID_TOKEN)
                        .POST(BodyPublishers.ofString(INITIALIZE_BODY))
                        .build(),
                BodyHandlers.ofString());
        assertNotEquals(401, response.statusCode(),
                () -> "valid token must not be rejected; got " + response.statusCode() + " body=" + response.body());
    }
}
