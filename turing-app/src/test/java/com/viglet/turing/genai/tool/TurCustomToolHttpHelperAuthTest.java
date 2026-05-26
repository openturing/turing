/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the T39 chainable auth helpers
 * ({@code http.bearer(...).postJson(...)} / {@code http.basic(...).getJson(...)})
 * and the reserved-slot auto-auth (writes to {@code __bearer_token} /
 * {@code __basic_user} + {@code __basic_password} translate into the next
 * request's {@code Authorization} header without explicit boilerplate).
 *
 * <p>A throwaway JDK {@link HttpServer} stands in for the upstream API so
 * the tests can capture the actual headers and body the helper sent — same
 * pattern used by {@code TurSEStopWordTest} so we stay consistent with the
 * project's existing in-process HTTP test style (no MockWebServer / WireMock
 * dependency added).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurCustomToolHttpHelperAuthTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    // ─────────────────────── chainable bearer / basic ───────────────────────

    @Test
    @DisplayName("http.bearer(token).getJson(url) sends Authorization: Bearer <token>")
    void bearerChain_attachesAuthorizationHeader() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper();

        Object result = http.bearer("abc123").getJson(baseUrl);

        assertThat(capturedAuth.get()).isEqualTo("Bearer abc123");
        assertThat(result).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("http.basic(user, pw).postJson(url, body) sends Authorization: Basic base64(user:pw)")
    void basicChain_attachesAuthorizationHeader() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper();
        String expectedB64 = Base64.getEncoder()
                .encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8));

        http.basic("alice", "s3cret").postJson(baseUrl, Map.of("k", "v"));

        assertThat(capturedAuth.get()).isEqualTo("Basic " + expectedB64);
    }

    @Test
    @DisplayName("bearer() is immutable — original helper still has no auth, chained returns new instance")
    void bearer_isImmutable_doesNotMutateOriginal() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper();

        TurCustomToolHttpHelper authed = http.bearer("t");
        http.getJson(baseUrl);

        assertThat(capturedAuth.get()).as("original helper makes an unauthenticated call").isNull();
        assertThat(authed).isNotSameAs(http);

        authed.getJson(baseUrl);
        assertThat(capturedAuth.get()).isEqualTo("Bearer t");
    }

    @Test
    @DisplayName("bearer(null) returns a helper with no explicit auth (clears the chain)")
    void bearer_nullClearsExplicitAuth() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper();

        http.bearer("first").bearer(null).getJson(baseUrl);

        assertThat(capturedAuth.get()).isNull();
    }

    @Test
    @DisplayName("basic(user, null) sends empty password — same as curl -u user:")
    void basic_nullPasswordIsEmptyString() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper();
        String expectedB64 = Base64.getEncoder()
                .encodeToString("alice:".getBytes(StandardCharsets.UTF_8));

        http.basic("alice", null).getJson(baseUrl);

        assertThat(capturedAuth.get()).isEqualTo("Basic " + expectedB64);
    }

    // ─────────────────────── reserved-slot auto-auth ───────────────────────

    @Test
    @DisplayName("__bearer_token slot auto-attaches Authorization: Bearer to every http.* call")
    void slot_bearerToken_autoAttaches() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BEARER_TOKEN)).thenReturn("auto-tok");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);
        http.getJson(baseUrl);

        assertThat(capturedAuth.get()).isEqualTo("Bearer auto-tok");
    }

    @Test
    @DisplayName("__basic_user + __basic_password slots auto-attach Authorization: Basic")
    void slot_basicCredentials_autoAttach() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BASIC_USER)).thenReturn("bob");
        when(slots.get(TurCustomToolHttpHelper.SLOT_BASIC_PASSWORD)).thenReturn("pw");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);
        http.postJson(baseUrl, Map.of("k", "v"));

        String expectedB64 = Base64.getEncoder()
                .encodeToString("bob:pw".getBytes(StandardCharsets.UTF_8));
        assertThat(capturedAuth.get()).isEqualTo("Basic " + expectedB64);
    }

    @Test
    @DisplayName("explicit bearer() overrides slot auto-auth — chain wins")
    void explicitBearer_overridesSlotAutoAuth() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BEARER_TOKEN)).thenReturn("slot-tok");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);
        http.bearer("override-tok").getJson(baseUrl);

        assertThat(capturedAuth.get()).isEqualTo("Bearer override-tok");
    }

    @Test
    @DisplayName("caller-supplied Authorization header beats both chain and slot")
    void callerHeader_overridesEverything() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BEARER_TOKEN)).thenReturn("slot-tok");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);
        http.bearer("chain-tok").getJson(baseUrl, Map.of("Authorization", "Custom xyz"));

        assertThat(capturedAuth.get()).isEqualTo("Custom xyz");
    }

    @Test
    @DisplayName("bearer slot beats basic slot when both are set")
    void slot_bearerWinsOverBasic() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BEARER_TOKEN)).thenReturn("tok");
        when(slots.get(TurCustomToolHttpHelper.SLOT_BASIC_USER)).thenReturn("u");
        when(slots.get(TurCustomToolHttpHelper.SLOT_BASIC_PASSWORD)).thenReturn("p");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);
        http.getJson(baseUrl);

        assertThat(capturedAuth.get()).isEqualTo("Bearer tok");
    }

    @Test
    @DisplayName("blank __bearer_token slot is treated as unset")
    void slot_blankBearer_isIgnored() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BEARER_TOKEN)).thenReturn("   ");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);
        http.getJson(baseUrl);

        assertThat(capturedAuth.get()).isNull();
    }

    @Test
    @DisplayName("no slots + no chain → no Authorization header on request")
    void noAuth_noHeader() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper();
        http.getJson(baseUrl);

        assertThat(capturedAuth.get()).isNull();
    }

    // ─────────────────────── log-redaction ───────────────────────

    @Test
    @DisplayName("toString() never echoes the bearer token — only REDACTED marker")
    void toString_redactsBearerToken() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper().bearer("super-secret-token");

        String repr = http.toString();

        assertThat(repr).contains("auth=REDACTED");
        assertThat(repr).doesNotContain("super-secret-token");
        assertThat(repr).doesNotContain("Bearer");
    }

    @Test
    @DisplayName("toString() never echoes Basic credentials")
    void toString_redactsBasicCredentials() {
        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper().basic("alice", "topsecret");

        String repr = http.toString();

        assertThat(repr).contains("auth=REDACTED");
        assertThat(repr).doesNotContain("alice");
        assertThat(repr).doesNotContain("topsecret");
    }

    @Test
    @DisplayName("toString() reports 'auth=none' when no chain auth is set, even with slot-bound credentials")
    void toString_unauthenticatedShowsNone() {
        TurCustomToolSlotHelper slots = mock(TurCustomToolSlotHelper.class);
        when(slots.get(TurCustomToolHttpHelper.SLOT_BEARER_TOKEN)).thenReturn("slot-tok");

        TurCustomToolHttpHelper http = new TurCustomToolHttpHelper(slots);

        // Slot auth is resolved lazily per-request — never serialised into toString.
        assertThat(http.toString()).contains("auth=none").doesNotContain("slot-tok");
    }
}
