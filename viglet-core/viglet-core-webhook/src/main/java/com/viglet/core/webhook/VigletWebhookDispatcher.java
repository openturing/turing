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
package com.viglet.core.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Neutral outbound-webhook delivery engine shared across Viglet products:
 * signs a request body with HMAC-SHA256 (when a secret is supplied) and POSTs
 * it over the JDK {@link HttpClient} with a bounded linear retry/back-off loop.
 *
 * <p>Consolidates the duplicated dispatch logic from Turing's
 * {@code TurChatWebhookService} (custom methods/headers/auth, no retry) and
 * Shio's {@code ShWebhookDispatcher}/{@code ShWebhookSigner} (HMAC + retry).
 *
 * <p><b>Threading.</b> {@link #deliver} is synchronous and runs the retry loop
 * (including its back-off sleeps) on the calling thread — products that already
 * dispatch off a background pool call this directly. {@link #dispatchAsync}
 * hands the call to an internal daemon pool for fire-and-forget delivery.
 *
 * <p>Zero Spring dependency: register it as a {@code @Bean} in the consuming
 * app. {@link AutoCloseable} shuts down the lazily-created async pool.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletWebhookDispatcher implements AutoCloseable {

    /** Default TCP connect timeout when none is supplied at construction. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;

    /** Lazily created on first {@link #dispatchAsync} so sync-only callers pay nothing. */
    private volatile ExecutorService asyncPool;
    private final AtomicInteger threadCounter = new AtomicInteger();

    public VigletWebhookDispatcher() {
        this(DEFAULT_CONNECT_TIMEOUT);
    }

    public VigletWebhookDispatcher(Duration connectTimeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout)
                .build());
    }

    /** For tests / custom clients (e.g. a shared, pre-configured {@link HttpClient}). */
    public VigletWebhookDispatcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Delivers {@code request} synchronously, retrying per {@code policy}. Never
     * throws for transport/HTTP failures — the outcome is reported in the
     * returned {@link VigletWebhookDeliveryResult}.
     */
    public VigletWebhookDeliveryResult deliver(VigletWebhookRequest request, VigletWebhookRetryPolicy policy) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            return VigletWebhookDeliveryResult.failed(0, 0, "Webhook target URL is required");
        }
        VigletWebhookRetryPolicy p = policy == null ? VigletWebhookRetryPolicy.defaults() : policy;
        HttpRequest httpRequest = buildHttpRequest(request, p.readTimeout());

        int lastStatus = 0;
        String lastError = null;
        for (int attempt = 1; attempt <= p.maxAttempts(); attempt++) {
            Duration wait = p.backoffBefore(attempt);
            if (!wait.isZero() && sleepInterrupted(wait)) {
                return VigletWebhookDeliveryResult.failed(lastStatus, attempt - 1,
                        "Interrupted before delivery");
            }
            try {
                HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                lastStatus = response.statusCode();
                if (lastStatus >= 200 && lastStatus < 300) {
                    return VigletWebhookDeliveryResult.ok(lastStatus, attempt);
                }
                lastError = "HTTP " + lastStatus;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return VigletWebhookDeliveryResult.failed(lastStatus, attempt, "Interrupted: " + e.getMessage());
            } catch (Exception e) {
                lastStatus = 0;
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        return VigletWebhookDeliveryResult.failed(lastStatus, p.maxAttempts(),
                "Webhook delivery failed after " + p.maxAttempts() + " attempt(s): " + lastError);
    }

    /** Fire-and-forget delivery on the internal daemon pool. */
    public CompletableFuture<VigletWebhookDeliveryResult> dispatchAsync(VigletWebhookRequest request,
            VigletWebhookRetryPolicy policy) {
        return CompletableFuture.supplyAsync(() -> deliver(request, policy), asyncPool());
    }

    private HttpRequest buildHttpRequest(VigletWebhookRequest request, Duration readTimeout) {
        byte[] body = request.body() == null ? new byte[0] : request.body();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(readTimeout);

        if (request.contentType() != null && !request.contentType().isBlank()) {
            builder.header("Content-Type", request.contentType());
        }
        for (Map.Entry<String, String> h : request.headers().entrySet()) {
            builder.header(h.getKey(), h.getValue() == null ? "" : h.getValue());
        }
        if (request.signed()) {
            builder.header(request.signatureHeaderName(),
                    VigletWebhookSigner.header(request.signingSecret(), body));
        }
        // Body-bearing methods send the bytes; GET/DELETE carry none.
        if (request.bodyBearing()) {
            builder.method(request.method().toUpperCase(java.util.Locale.ROOT),
                    HttpRequest.BodyPublishers.ofByteArray(body));
        } else {
            builder.method(request.method().toUpperCase(java.util.Locale.ROOT),
                    HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private static boolean sleepInterrupted(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private ExecutorService asyncPool() {
        ExecutorService pool = asyncPool;
        if (pool == null) {
            synchronized (this) {
                pool = asyncPool;
                if (pool == null) {
                    pool = Executors.newFixedThreadPool(2, r -> {
                        Thread t = new Thread(r, "viglet-webhook-dispatch-" + threadCounter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    });
                    asyncPool = pool;
                }
            }
        }
        return pool;
    }

    @Override
    public void close() {
        ExecutorService pool = asyncPool;
        if (pool != null) {
            pool.shutdown();
        }
    }
}
