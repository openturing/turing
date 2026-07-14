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

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.utils.TurFileUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * T739 / §XLVIII — the {@code HEADLESS} fetch backend. POSTs the target URL to a
 * {@code browserless/chromium} sidecar's {@code /content} endpoint, which loads
 * the page in a real Chromium (running its JavaScript) and returns the rendered
 * DOM as HTML. That HTML is then parsed to text by the same Tika path the other
 * fetchers use, so a client-rendered SPA — invisible to the {@code SIMPLE}
 * backend — yields its real body text.
 *
 * <p>Fail-soft: a missing sidecar URL, a non-2xx response, a timeout or any I/O
 * error returns an empty {@link TurFileAttributes} rather than throwing. The URL
 * is SSRF-guarded before the sidecar is contacted, and callers are expected to
 * keep the sidecar on an internal network so it cannot be used as an SSRF pivot.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurHeadlessUrlFetcher implements TurUrlFetcher {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public TurFileAttributes fetch(URL url, TurUrlFetchConfig config) {
        if (config == null || !config.hasBrowserless()) {
            log.warn("[UrlFetch] HEADLESS requested but no browserless sidecar is configured");
            return new TurFileAttributes();
        }
        String target = url.toString();
        if (!TurFileUtils.isAllowedRemoteUrlString(target)) {
            log.warn("[UrlFetch] blocked or disallowed URL for headless render: {}", target);
            return new TurFileAttributes();
        }
        try {
            HttpRequest request = buildRequest(target, config);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[UrlFetch] browserless returned HTTP {} for {}",
                        response.statusCode(), target);
                return new TurFileAttributes();
            }
            return TurFileUtils.htmlToText(response.body(), target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[UrlFetch] headless render interrupted for {}", target);
            return new TurFileAttributes();
        } catch (Exception e) {
            log.warn("[UrlFetch] headless render failed for {}: {}", target, e.getMessage());
            return new TurFileAttributes();
        }
    }

    private HttpRequest buildRequest(String target, TurUrlFetchConfig config) {
        String base = TurUrlFetchService.stripTrailingSlash(config.browserlessUrl().trim());
        String endpoint = base + "/content";
        if (StringUtils.isNotBlank(config.browserlessToken())) {
            endpoint += "?token=" + config.browserlessToken().trim();
        }
        String body = "{\"url\":\"" + jsonEscape(target) + "\"}";
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public TurUrlFetchMode getBackend() {
        return TurUrlFetchMode.HEADLESS;
    }
}
