/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.urlfetch;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.utils.TurFileUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * T739 / §XLVIII — the single entry point for turning a content URL into text.
 * Guards the URL once (SSRF/allowlist), resolves the effective
 * {@link TurUrlFetchMode} via {@link TurUrlFetchConfigResolver}, and dispatches to
 * the matching {@link TurUrlFetcher}:
 *
 * <ul>
 *   <li>{@code SIMPLE} — cheap HTTP+Tika only (today's behavior).</li>
 *   <li>{@code HEADLESS} — always render in the browserless sidecar; falls back to
 *       {@code SIMPLE} when no sidecar is configured (fail-open).</li>
 *   <li>{@code AUTO} — run {@code SIMPLE}, and only when it extracts fewer than
 *       {@code autoMinChars} characters (the SPA-shell case) <b>and</b> a sidecar
 *       is configured, escalate to {@code HEADLESS}, keeping whichever result has
 *       more text.</li>
 * </ul>
 *
 * <p>Never throws and never returns {@code null}: a blocked/blank/failed fetch
 * yields an empty {@link TurFileAttributes} so callers record the source as
 * failed rather than aborting a batch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurUrlFetchService {

    private final Map<TurUrlFetchMode, TurUrlFetcher> byBackend =
            new EnumMap<>(TurUrlFetchMode.class);
    private final TurUrlFetchConfigResolver configResolver;
    private final HttpClient probeClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TurUrlFetchService(List<TurUrlFetcher> fetchers,
            TurUrlFetchConfigResolver configResolver) {
        for (TurUrlFetcher fetcher : fetchers) {
            byBackend.put(fetcher.getBackend(), fetcher);
        }
        this.configResolver = configResolver;
        log.info("[UrlFetch] backends registered: {}", byBackend.keySet());
    }

    /**
     * Fetch and extract text for a user-supplied URL string, applying the
     * configured mode. Returns an empty {@link TurFileAttributes} (never
     * {@code null}) when the URL is blank/blocked/malformed or every backend
     * yields nothing.
     */
    public TurFileAttributes fetch(String urlString) {
        if (StringUtils.isBlank(urlString) || !TurFileUtils.isAllowedRemoteUrlString(urlString)) {
            log.warn("[UrlFetch] blocked or blank URL: {}", urlString);
            return new TurFileAttributes();
        }
        URL url;
        try {
            url = URI.create(urlString).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            log.warn("[UrlFetch] malformed URL {}: {}", urlString, e.getMessage());
            return new TurFileAttributes();
        }
        return dispatch(url, configResolver.resolve());
    }

    /**
     * T791 / §LIV.2 (Block BF) — fetch a URL's <strong>raw</strong> body as text,
     * <em>without</em> the Tika text-extraction the {@link #fetch(String)} path runs.
     * A structured JSON / NDJSON feed must be deserialized verbatim; running it
     * through Tika would strip the JSON syntax and mangle the records. This reuses
     * the exact same T739 security posture — the {@code isAllowedRemoteUrlString}
     * SSRF/allowlist guard and the resolved request timeout — but returns the HTTP
     * response body as-is.
     *
     * <p>Never throws: returns {@code null} on a blank/blocked/malformed URL, a
     * non-2xx response, or any transport error, so a scheduled ingester records the
     * source as failed rather than aborting its batch.
     */
    public String fetchRaw(String urlString) {
        if (StringUtils.isBlank(urlString) || !TurFileUtils.isAllowedRemoteUrlString(urlString)) {
            log.warn("[UrlFetch] raw fetch blocked or blank URL: {}", urlString);
            return null;
        }
        URI uri;
        try {
            uri = URI.create(urlString);
        } catch (IllegalArgumentException e) {
            log.warn("[UrlFetch] raw fetch malformed URL {}: {}", urlString, e.getMessage());
            return null;
        }
        int timeoutSeconds = Math.max(1, configResolver.resolve().timeoutSeconds());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json, application/x-ndjson, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = probeClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return response.body();
            }
            log.warn("[UrlFetch] raw fetch of {} returned HTTP {}", urlString, response.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[UrlFetch] raw fetch of {} interrupted", urlString);
            return null;
        } catch (Exception e) {
            log.warn("[UrlFetch] raw fetch of {} failed: {}", urlString, e.getMessage());
            return null;
        }
    }

    /**
     * Route an already-guarded URL to the backend selected by {@code config.mode()}.
     * Package-private so the mode/AUTO logic can be unit-tested without network
     * access or the SSRF guard.
     */
    TurFileAttributes dispatch(URL url, TurUrlFetchConfig config) {
        return switch (config.mode()) {
            case SIMPLE -> simple(url, config);
            case HEADLESS -> headlessOrSimple(url, config);
            case AUTO -> auto(url, config);
        };
    }

    private TurFileAttributes auto(URL url, TurUrlFetchConfig config) {
        TurFileAttributes simple = simple(url, config);
        int simpleLen = textLength(simple);
        if (simpleLen >= config.autoMinChars() || !config.hasBrowserless()) {
            if (simpleLen < config.autoMinChars()) {
                log.info("[UrlFetch] AUTO: {} yielded {} chars (< {}) but no sidecar; keeping SIMPLE",
                        url, simpleLen, config.autoMinChars());
            }
            return simple;
        }
        log.info("[UrlFetch] AUTO: {} yielded {} chars (< {}); escalating to HEADLESS",
                url, simpleLen, config.autoMinChars());
        TurFileAttributes headless = headless(url, config);
        // Keep whichever extracted more text (the render can still fail).
        return textLength(headless) > simpleLen ? headless : simple;
    }

    private TurFileAttributes headlessOrSimple(URL url, TurUrlFetchConfig config) {
        if (!config.hasBrowserless()) {
            log.warn("[UrlFetch] HEADLESS mode but no browserless sidecar; falling back to SIMPLE for {}",
                    url);
            return simple(url, config);
        }
        return headless(url, config);
    }

    private TurFileAttributes simple(URL url, TurUrlFetchConfig config) {
        return byBackend.get(TurUrlFetchMode.SIMPLE).fetch(url, config);
    }

    private TurFileAttributes headless(URL url, TurUrlFetchConfig config) {
        TurUrlFetcher fetcher = byBackend.get(TurUrlFetchMode.HEADLESS);
        if (fetcher == null) {
            return new TurFileAttributes();
        }
        return fetcher.fetch(url, config);
    }

    private static int textLength(TurFileAttributes attributes) {
        if (attributes == null || attributes.getContent() == null) {
            return 0;
        }
        return attributes.getContent().strip().length();
    }

    /**
     * Probe whether the configured browserless sidecar is reachable, for the
     * admin "Check browserless" button. Never sends anything sensitive — only an
     * availability flag, the reported browser version and an error string.
     */
    public TurBrowserlessStatus checkBrowserless() {
        TurUrlFetchConfig config = configResolver.resolve();
        if (!config.hasBrowserless()) {
            return new TurBrowserlessStatus(false, null, "No browserless URL configured");
        }
        String base = stripTrailingSlash(config.browserlessUrl().trim());
        String endpoint = base + "/json/version";
        if (StringUtils.isNotBlank(config.browserlessToken())) {
            endpoint += "?token=" + config.browserlessToken().trim();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.min(10, config.timeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<String> response = probeClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return new TurBrowserlessStatus(true, extractBrowserVersion(response.body()), null);
            }
            return new TurBrowserlessStatus(false, null, "HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TurBrowserlessStatus(false, null, "probe interrupted");
        } catch (Exception e) {
            return new TurBrowserlessStatus(false, null,
                    "not reachable: " + e.getMessage());
        }
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Pull the {@code "Browser"} field out of the DevTools {@code /json/version} JSON, best-effort. */
    private static String extractBrowserVersion(String json) {
        if (json == null) {
            return null;
        }
        int idx = json.indexOf("\"Browser\"");
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        int firstQuote = json.indexOf('"', colon + 1);
        int lastQuote = json.indexOf('"', firstQuote + 1);
        return (firstQuote > 0 && lastQuote > firstQuote)
                ? json.substring(firstQuote + 1, lastQuote)
                : null;
    }

    /** Probe result for the browserless sidecar. */
    public record TurBrowserlessStatus(boolean available, String version, String error) {
    }
}
