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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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
 * <p><b>Chainable auth helpers (T39, since 2026.3.1)</b> — scripts that hit
 * authenticated APIs can do {@code http.bearer(token).postJson(url, body)} or
 * {@code http.basic(user, pw).getJson(url)} instead of re-implementing the
 * {@code Authorization} header per call. {@link #bearer(String)} and
 * {@link #basic(String, String)} are immutable: each returns a NEW helper
 * with the auth set; the original is unchanged. That way one script can
 * safely interleave authenticated and unauthenticated calls without leaking
 * credentials into shared state.
 *
 * <p><b>Reserved slot prefix {@code __} for auto-auth (T39)</b> — when the
 * helper is bound to a {@link TurCustomToolSlotHelper}, the following slot
 * names are reserved as credential carriers and AUTOMATICALLY drive the
 * {@code Authorization} header on every {@code http.*} call:
 * <ul>
 *   <li>{@code __bearer_token} → {@code Authorization: Bearer <token>}</li>
 *   <li>{@code __basic_user} + {@code __basic_password} →
 *       {@code Authorization: Basic <base64(user:password)>}</li>
 * </ul>
 * Explicit {@code bearer()}/{@code basic()} chain wins over the slot
 * fallback. Reserved slots are never echoed in {@link #toString()} and the
 * resolved header is redacted from any helper-emitted log output.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public class TurCustomToolHttpHelper {

    // --- S1192: extracted duplicated literals ---
    private static final String AUTHORIZATION = "Authorization";


    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Reserved chat-flow slot name for an OAuth/JWT bearer token. When set,
     * every {@code http.*} call attaches {@code Authorization: Bearer <value>}
     * unless the script already provided an explicit {@link #bearer(String)}
     * chain or {@code Authorization} header override. The {@code __} prefix
     * marks the slot as credential material — engine policy must treat it as
     * write-only outside the active conversation.
     */
    public static final String SLOT_BEARER_TOKEN = "__bearer_token";

    /**
     * Reserved chat-flow slot name for the username half of HTTP Basic auth.
     * Pairs with {@link #SLOT_BASIC_PASSWORD}; a write to one without the
     * other still triggers Basic auto-auth with an empty counterpart
     * (matches the semantics of {@code curl -u user:}).
     */
    public static final String SLOT_BASIC_USER = "__basic_user";

    /**
     * Reserved chat-flow slot name for the password half of HTTP Basic auth.
     * Treated as opaque — base64-encoded together with {@link #SLOT_BASIC_USER}
     * when no explicit auth chain is set on the helper.
     */
    public static final String SLOT_BASIC_PASSWORD = "__basic_password";

    private final RestClient restClient;
    private final String authorizationHeader;
    private final TurCustomToolSlotHelper slots;

    /**
     * Default helper for absolute URLs. Scripts pass full URLs to each call.
     * Auto-auth from reserved slots is disabled — use
     * {@link #TurCustomToolHttpHelper(TurCustomToolSlotHelper)} to enable it.
     */
    public TurCustomToolHttpHelper() {
        this(null, DEFAULT_TIMEOUT, null);
    }

    /**
     * Helper bound to a base URL — scripts can pass relative paths.
     */
    public TurCustomToolHttpHelper(String baseUrl) {
        this(baseUrl, DEFAULT_TIMEOUT, null);
    }

    public TurCustomToolHttpHelper(String baseUrl, Duration timeout) {
        this(baseUrl, timeout, null);
    }

    /**
     * Conversation-aware helper that reads reserved credential slots
     * ({@code __bearer_token} / {@code __basic_user} + {@code __basic_password})
     * for automatic {@code Authorization} header injection. This is the
     * constructor used by {@link TurCustomToolCallbackService} for the
     * {@code http} binding so Custom Tools get auto-auth for free.
     */
    public TurCustomToolHttpHelper(TurCustomToolSlotHelper slots) {
        this(null, DEFAULT_TIMEOUT, slots);
    }

    private TurCustomToolHttpHelper(String baseUrl, Duration timeout,
            TurCustomToolSlotHelper slots) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(timeout);
        rf.setReadTimeout(timeout);
        RestClient.Builder builder = RestClient.builder().requestFactory(rf);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder = builder.baseUrl(baseUrl);
        }
        this.restClient = builder.build();
        this.authorizationHeader = null;
        this.slots = slots;
    }

    private TurCustomToolHttpHelper(RestClient restClient,
            String authorizationHeader, TurCustomToolSlotHelper slots) {
        this.restClient = restClient;
        this.authorizationHeader = authorizationHeader;
        this.slots = slots;
    }

    /**
     * Returns a new helper that pins {@code Authorization: Bearer <token>}
     * on every subsequent request. Chainable — typical usage:
     * {@code http.bearer(args.api_key).postJson(url, body)}. The original
     * helper is not mutated; auth-state never escapes a single chained
     * call site. Passing {@code null} or blank yields a helper with no
     * explicit auth (falls back to slot-based auto-auth, if any).
     */
    public TurCustomToolHttpHelper bearer(String token) {
        if (token == null || token.isBlank()) {
            return new TurCustomToolHttpHelper(restClient, null, slots);
        }
        return new TurCustomToolHttpHelper(restClient, "Bearer " + token, slots);
    }

    /**
     * Returns a new helper that pins {@code Authorization: Basic
     * <base64(user:password)>} on every subsequent request. Same immutable
     * chainable semantics as {@link #bearer(String)}; passing {@code null}
     * or empty {@code user} resets the explicit auth.
     */
    public TurCustomToolHttpHelper basic(String user, String password) {
        if (user == null || user.isEmpty()) {
            return new TurCustomToolHttpHelper(restClient, null, slots);
        }
        String value = user + ":" + (password == null ? "" : password);
        String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return new TurCustomToolHttpHelper(restClient, "Basic " + encoded, slots);
    }

    /**
     * Resolves the {@code Authorization} header to attach to the next
     * request. Precedence: explicit {@link #bearer(String)} /
     * {@link #basic(String, String)} chain &gt; reserved slot credentials
     * (bearer first, then basic) &gt; none.
     */
    private String resolveAuthorizationHeader() {
        if (authorizationHeader != null) {
            return authorizationHeader;
        }
        if (slots == null) {
            return null;
        }
        String bearerToken = slots.get(SLOT_BEARER_TOKEN);
        if (bearerToken != null && !bearerToken.isBlank()) {
            return "Bearer " + bearerToken;
        }
        String basicUser = slots.get(SLOT_BASIC_USER);
        if (basicUser != null && !basicUser.isEmpty()) {
            String basicPassword = slots.get(SLOT_BASIC_PASSWORD);
            String value = basicUser + ":" + (basicPassword == null ? "" : basicPassword);
            String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            return "Basic " + encoded;
        }
        return null;
    }

    /**
     * GET {@code url} and parse the response body as JSON. Headers are
     * optional; pass {@code null} to skip. Caller-supplied {@code Authorization}
     * always wins over the chain/slot fallback, so a script can override
     * auto-auth on a single call when needed.
     *
     * @return a {@link Map}, {@link List}, or scalar — whatever the JSON
     *         body deserializes to. Returns {@code null} when the response
     *         body is empty.
     */
    public Object getJson(String url, Map<String, String> headers) {
        var spec = restClient.get().uri(url);
        String auth = resolveAuthorizationHeader();
        if (auth != null && !hasHeader(headers, AUTHORIZATION)) {
            spec = spec.header(AUTHORIZATION, auth);
        }
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
     * are optional; pass {@code null} to skip. Auth precedence matches
     * {@link #getJson(String, Map)}.
     */
    public Object postJson(String url, Object body, Map<String, String> headers) {
        var spec = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json");
        String auth = resolveAuthorizationHeader();
        if (auth != null && !hasHeader(headers, AUTHORIZATION)) {
            spec = spec.header(AUTHORIZATION, auth);
        }
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

    private static boolean hasHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Redacted representation — never echo bearer tokens or Basic credentials
     * via an accidental {@code log.info(http)} from a customer script. The
     * presence of explicit chain-auth and slot-binding is surfaced; the
     * actual token never is.
     */
    @Override
    public String toString() {
        return "TurCustomToolHttpHelper{auth=" + (authorizationHeader == null ? "none" : "REDACTED")
                + ", slots=" + (slots == null ? "none" : "bound") + "}";
    }
}
