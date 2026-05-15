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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Convenience helper exposed to Custom Tool Groovy scripts under the binding
 * variable name {@code http}. Wraps {@link RestClient} so customer-authored
 * scripts can do {@code http.postJson(url, body)} or {@code http.getJson(url)}
 * without re-implementing HTTP boilerplate. Returns parsed JSON
 * ({@link Map} / {@link List}) — scripts treat the result as a Groovy
 * structure directly.
 *
 * <p>One instance is constructed per Custom Tool invocation by
 * {@link TurCustomToolCallbackService}; not a Spring singleton because we
 * want the timeout and base-URL to be tunable per call without contaminating
 * shared state. Construction is cheap (no connection until first call).
 *
 * <p>Bounded by 10 second timeouts to keep slow upstream services from
 * stalling a chat turn indefinitely.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public class TurCustomToolHttpHelper {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    /**
     * Default helper for absolute URLs. Scripts pass full URLs to each call.
     */
    public TurCustomToolHttpHelper() {
        this(null, DEFAULT_TIMEOUT);
    }

    /**
     * Helper bound to a base URL — scripts can pass relative paths.
     */
    public TurCustomToolHttpHelper(String baseUrl) {
        this(baseUrl, DEFAULT_TIMEOUT);
    }

    public TurCustomToolHttpHelper(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(timeout);
        rf.setReadTimeout(timeout);
        RestClient.Builder builder = RestClient.builder().requestFactory(rf);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder = builder.baseUrl(baseUrl);
        }
        this.restClient = builder.build();
    }

    /**
     * GET {@code url} and parse the response body as JSON. Headers are
     * optional; pass {@code null} to skip.
     *
     * @return a {@link Map}, {@link List}, or scalar — whatever the JSON
     *         body deserializes to. Returns {@code null} when the response
     *         body is empty.
     */
    public Object getJson(String url, Map<String, String> headers) {
        var spec = restClient.get().uri(url);
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                spec = spec.header(h.getKey(), h.getValue());
            }
        }
        return spec.retrieve().body(Object.class);
    }

    /** Convenience overload — no headers. */
    public Object getJson(String url) {
        return getJson(url, null);
    }

    /**
     * POST {@code url} with {@code body} (any JSON-serializable object —
     * usually a {@link Map}) and parse the response body as JSON. Headers
     * are optional; pass {@code null} to skip.
     */
    public Object postJson(String url, Object body, Map<String, String> headers) {
        var spec = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                spec = spec.header(h.getKey(), h.getValue());
            }
        }
        return spec.body(body).retrieve().body(Object.class);
    }

    /** Convenience overload — no headers. */
    public Object postJson(String url, Object body) {
        return postJson(url, body, null);
    }
}
