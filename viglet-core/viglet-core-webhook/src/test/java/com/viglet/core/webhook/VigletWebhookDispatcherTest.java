/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Wire-contract coverage for {@link VigletWebhookDispatcher} against a real
 * loopback HTTP server (JDK built-in, no extra dependency): the happy POST,
 * the HMAC signature header, custom headers + method, the retry/back-off loop,
 * and exhaustion.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class VigletWebhookDispatcherTest {

    private HttpServer server;
    private final VigletWebhookDispatcher dispatcher = new VigletWebhookDispatcher();

    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastSignature = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastCustomHeader = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        dispatcher.close();
    }

    /** Starts a loopback server whose /hook context returns the given statuses in sequence. */
    private String stub(int... statusSequence) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<Integer> statuses = new CopyOnWriteArrayList<>();
        for (int s : statusSequence) {
            statuses.add(s);
        }
        server.createContext("/hook", exchange -> {
            int i = hits.getAndIncrement();
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastSignature.set(exchange.getRequestHeaders().getFirst("X-Viglet-Signature"));
            lastMethod.set(exchange.getRequestMethod());
            lastCustomHeader.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            int status = statuses.get(Math.min(i, statuses.size() - 1));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @Test
    void delivers2xxOnFirstAttempt() throws IOException {
        String url = stub(200);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url)
                .body("{\"event\":\"test\"}".getBytes(StandardCharsets.UTF_8))
                .build();

        VigletWebhookDeliveryResult result = dispatcher.deliver(request, VigletWebhookRetryPolicy.defaults());

        assertThat(result.delivered()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(lastBody.get()).isEqualTo("{\"event\":\"test\"}");
        assertThat(lastMethod.get()).isEqualTo("POST");
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void addsHmacSignatureWhenSecretPresent() throws IOException {
        String url = stub(204);
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url).body(body).signingSecret("topsecret").build();

        VigletWebhookDeliveryResult result = dispatcher.deliver(request, VigletWebhookRetryPolicy.once(Duration.ofSeconds(2)));

        assertThat(result.delivered()).isTrue();
        assertThat(lastSignature.get()).isEqualTo(VigletWebhookSigner.header("topsecret", body));
    }

    @Test
    void omitsSignatureWhenNoSecret() throws IOException {
        String url = stub(200);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url).body("{}".getBytes(StandardCharsets.UTF_8)).build();

        dispatcher.deliver(request, VigletWebhookRetryPolicy.once(Duration.ofSeconds(2)));

        assertThat(lastSignature.get()).isNull();
    }

    @Test
    void sendsCustomHeadersAndMethod() throws IOException {
        String url = stub(200);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url).method("PUT")
                .headers(Map.of("X-Api-Key", "abc123"))
                .body("{}".getBytes(StandardCharsets.UTF_8))
                .build();

        dispatcher.deliver(request, VigletWebhookRetryPolicy.once(Duration.ofSeconds(2)));

        assertThat(lastMethod.get()).isEqualTo("PUT");
        assertThat(lastCustomHeader.get()).isEqualTo("abc123");
    }

    @Test
    void retriesUntil2xxThenStops() throws IOException {
        String url = stub(500, 500, 200);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url).body("{}".getBytes(StandardCharsets.UTF_8)).build();
        // Zero back-off keeps the test fast; 3 attempts allowed.
        VigletWebhookRetryPolicy policy = new VigletWebhookRetryPolicy(3, Duration.ofSeconds(2), Duration.ZERO);

        VigletWebhookDeliveryResult result = dispatcher.deliver(request, policy);

        assertThat(result.delivered()).isTrue();
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(hits.get()).isEqualTo(3);
    }

    @Test
    void exhaustsAttemptsAndReportsFailure() throws IOException {
        String url = stub(503);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url).body("{}".getBytes(StandardCharsets.UTF_8)).build();
        VigletWebhookRetryPolicy policy = new VigletWebhookRetryPolicy(2, Duration.ofSeconds(2), Duration.ZERO);

        VigletWebhookDeliveryResult result = dispatcher.deliver(request, policy);

        assertThat(result.delivered()).isFalse();
        assertThat(result.statusCode()).isEqualTo(503);
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.error()).contains("2 attempt");
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void blankUrlFailsFastWithoutNetwork() {
        VigletWebhookDeliveryResult result = dispatcher.deliver(
                VigletWebhookRequest.builder().url("  ").build(), VigletWebhookRetryPolicy.defaults());
        assertThat(result.delivered()).isFalse();
        assertThat(result.attempts()).isZero();
        assertThat(result.error()).contains("required");
    }

    @Test
    void asyncDeliversThroughInternalPool() throws Exception {
        String url = stub(200);
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(url).body("{}".getBytes(StandardCharsets.UTF_8)).build();

        VigletWebhookDeliveryResult result = dispatcher
                .dispatchAsync(request, VigletWebhookRetryPolicy.once(Duration.ofSeconds(2)))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(result.delivered()).isTrue();
    }
}
