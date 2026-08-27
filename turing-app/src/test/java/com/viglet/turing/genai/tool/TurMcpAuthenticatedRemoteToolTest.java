/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.domain.mcp.TurMcpServerDomain;
import com.viglet.turing.domain.mcp.TurMcpServerRepositoryPort;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerTransportType;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T1031 / §XX.18 — the MCP half of federating to a vendor's own retrieval, over
 * the real transport.
 *
 * <p>The design named two paths to querying an index Turing does not own: a
 * third {@code TurSearchEnginePlugin}, or registering the vendor's MCP server as
 * an agent tool. The second was written as already available, and it was not:
 * until T1032 the outbound HTTP transport was built from the URL alone, so every
 * authenticated remote MCP server — which is what every commercial one is —
 * failed at {@code initialize()} and its tools silently never appeared.
 *
 * <p>{@link TurMcpToolCallbackServiceTest} proves the customizer produces the
 * right header. This proves the header reaches the wire through the transport
 * the SDK actually builds, against a server that refuses without it. Between the
 * two, "register the vendor's MCP endpoint and its retrieval becomes an agent
 * tool" is a tested claim rather than an assumed one.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurMcpAuthenticatedRemoteToolTest {

    private static final String TOKEN = "ims-access-token";

    @Mock
    private TurMcpServerRepositoryPort mcpServerRepositoryPort;
    @Mock
    private TurSecretCryptoService secretCryptoService;

    private TurMcpToolCallbackService service;
    private com.viglet.turing.properties.TurMcpClientProperty clientProperty;
    private HttpServer server;

    /** Every Authorization header the client presented, in order. */
    private final List<String> presentedAuth = new CopyOnWriteArrayList<>();
    /** Every request line, so a failure says what the client actually did. */
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        clientProperty = new com.viglet.turing.properties.TurMcpClientProperty();
        // T1037 — retry every turn by default here, so the credential assertions
        // below are not confounded by the new backoff. Its own tests set it.
        clientProperty.setFailureBackoff(java.time.Duration.ZERO);
        service = new TurMcpToolCallbackService(mcpServerRepositoryPort, clientProperty,
                new com.viglet.turing.spring.security.ssrf.TurSsrfGuard(),
                secretCryptoService);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        service.cleanup();
        server.stop(0);
    }

    /**
     * A vendor's MCP endpoint in miniature: it records the credential it was
     * presented and refuses, exactly as Adobe's Foundational Services or
     * GitHub's MCP server does without one.
     *
     * <p>It always refuses, including for the right token. Completing an MCP
     * handshake would prove something about this stub rather than about Turing,
     * and letting the client proceed only makes it wait out its 30-second
     * request timeout. What is under test is which bytes left Turing.
     */
    private void handle(HttpExchange exchange) throws IOException {
        presentedAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = exchange.getRequestBody().readAllBytes();
        requests.add(exchange.getRequestMethod() + " " + new String(body, StandardCharsets.UTF_8));
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
    }

    private TurMcpServerDomain remoteServer(String authTokenEncrypted) {
        return new TurMcpServerDomain("mcp-1", "Vendor Retrieval", null, null,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp", null, null, null,
                TurMcpServerConnectionType.HTTP, TurMcpServerTransportType.STREAMABLE_HTTP,
                authTokenEncrypted, 1);
    }

    @Test
    @DisplayName("A registered credential reaches the vendor's endpoint through the real transport")
    void credentialReachesTheWire() {
        when(secretCryptoService.decrypt("cipher")).thenReturn(TOKEN);
        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher")));

        service.getToolCallbacks();

        assertThat(requests).as("the client never contacted the endpoint").isNotEmpty();
        assertThat(presentedAuth).allSatisfy(auth -> assertThat(auth)
                .as("every request must carry the registered credential")
                .isEqualTo("Bearer " + TOKEN));
    }

    @Test
    @DisplayName("Without a credential nothing is presented — which is the T1032 bug, in one line")
    void withoutACredentialNothingIsPresented() {
        when(mcpServerRepositoryPort.findAllEnabled()).thenReturn(List.of(remoteServer(null)));

        // This is exactly what a customer saw before T1032: the endpoint has
        // nothing to authenticate, refuses, and the agent quietly has fewer
        // tools than the admin configured.
        assertThat(service.getToolCallbacks()).isEmpty();

        assertThat(requests).as("the client never contacted the endpoint").isNotEmpty();
        assertThat(presentedAuth).allSatisfy(auth -> assertThat(auth).isNull());
    }

    @Test
    @DisplayName("A stale credential is presented as it is, and refused — no silent fallback")
    void staleCredentialIsPresentedAndRefused() {
        when(secretCryptoService.decrypt("cipher")).thenReturn("stale-token");
        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher")));

        assertThat(service.getToolCallbacks()).isEmpty();
        assertThat(presentedAuth).containsOnly("Bearer stale-token");
    }

    // ---- T1037 / §CVI.2 — the chat path stops paying for a dead server -------

    @Test
    @DisplayName("With a backoff, a server that just failed is not contacted again next turn")
    void failedServerIsSkippedOnTheNextTurn() {
        clientProperty.setFailureBackoff(java.time.Duration.ofHours(1));
        when(secretCryptoService.decrypt("cipher")).thenReturn(TOKEN);
        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher")));

        service.getToolCallbacks();
        int afterFirstTurn = requests.size();
        assertThat(afterFirstTurn).isPositive();

        // The second turn must not touch the endpoint at all — that is the whole
        // point: the agent has no tools from it either way.
        assertThat(service.getToolCallbacks()).isEmpty();
        assertThat(requests).hasSize(afterFirstTurn);
    }

    @Test
    @DisplayName("A zero backoff keeps the legacy behaviour of retrying every turn")
    void zeroBackoffRetriesEveryTurn() {
        clientProperty.setFailureBackoff(java.time.Duration.ZERO);
        when(secretCryptoService.decrypt("cipher")).thenReturn(TOKEN);
        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher")));

        service.getToolCallbacks();
        int afterFirstTurn = requests.size();
        service.getToolCallbacks();

        assertThat(requests).hasSizeGreaterThan(afterFirstTurn);
    }

    @Test
    @DisplayName("Fixing the credential cancels the backoff, so an admin's edit takes effect at once")
    void fixingTheConnectionCancelsTheBackoff() {
        clientProperty.setFailureBackoff(java.time.Duration.ofHours(1));
        when(secretCryptoService.decrypt("cipher-bad")).thenReturn("stale-token");
        when(secretCryptoService.decrypt("cipher-good")).thenReturn(TOKEN);

        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher-bad")));
        service.getToolCallbacks();
        int afterFailure = requests.size();
        assertThat(afterFailure).isPositive();

        // Same server, corrected credential: waiting out the hour would be the
        // wrong answer to somebody who just fixed the problem.
        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher-good")));
        service.getToolCallbacks();

        assertThat(requests).hasSizeGreaterThan(afterFailure);
        assertThat(presentedAuth).contains("Bearer " + TOKEN);
    }

    @Test
    @DisplayName("The default timeout is well under the 30 seconds the chat path used to pay")
    void defaultTimeoutIsShort() {
        var defaults = new com.viglet.turing.properties.TurMcpClientProperty();

        assertThat(defaults.getRequestTimeout()).isLessThan(java.time.Duration.ofSeconds(30));
        assertThat(defaults.getFailureBackoff()).isPositive();
    }

    @Test
    @DisplayName("A nonsensical configured timeout falls back rather than making every server unreachable")
    void nonsensicalTimeoutFallsBack() {
        for (java.time.Duration bad : new java.time.Duration[] {null, java.time.Duration.ZERO,
                java.time.Duration.ofSeconds(-5)}) {
            clientProperty.setRequestTimeout(bad);
            when(secretCryptoService.decrypt("cipher")).thenReturn(TOKEN);
            when(mcpServerRepositoryPort.findAllEnabled())
                    .thenReturn(List.of(remoteServer("cipher")));

            // A zero or negative timeout would reject before the socket opened;
            // the request reaching the stub is the proof it fell back.
            int before = requests.size();
            service.getToolCallbacks();
            assertThat(requests).as("timeout %s must not stop the client connecting", bad)
                    .hasSizeGreaterThan(before);
        }
    }

    @Test
    @DisplayName("Rotating the credential reconnects, so a new token is presented without a restart")
    void rotatingTheCredentialReconnects() {
        when(secretCryptoService.decrypt("cipher-old")).thenReturn("old-token");
        when(secretCryptoService.decrypt("cipher-new")).thenReturn(TOKEN);

        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher-old")));
        service.getToolCallbacks();
        assertThat(presentedAuth).containsOnly("Bearer old-token");

        List<String> beforeRotation = new ArrayList<>(presentedAuth);
        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(remoteServer("cipher-new")));
        service.getToolCallbacks();

        assertThat(presentedAuth).hasSizeGreaterThan(beforeRotation.size());
        assertThat(presentedAuth.subList(beforeRotation.size(), presentedAuth.size()))
                .as("the rotated token must be the one presented next")
                .containsOnly("Bearer " + TOKEN);
    }
}
