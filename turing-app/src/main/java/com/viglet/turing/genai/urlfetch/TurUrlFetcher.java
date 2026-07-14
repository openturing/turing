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

import java.net.URL;

import com.viglet.turing.commons.file.TurFileAttributes;

/**
 * T739 / §XLVIII — pluggable URL content-fetch seam. A fetcher turns a remote URL
 * into extracted {@link TurFileAttributes} (text + metadata). Two backends ship:
 * the dependency-free {@code SIMPLE} HTTP+Tika path and the {@code HEADLESS}
 * browser-render path over a {@code browserless} sidecar; the orchestration
 * (including {@code AUTO} escalation) lives in {@link TurUrlFetchService}, which
 * resolves the active backend the same way {@code TurTranscriptionProviderFactory}
 * routes transcription backends.
 *
 * <p>Implementations are <b>fail-soft</b>: a fetch/parse failure returns an empty
 * {@link TurFileAttributes} (blank content), never throws, so a bad source is
 * recorded rather than aborting a batch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurUrlFetcher {

    /**
     * Fetch and extract text for {@code url}. The URL is assumed to have passed
     * the SSRF/allowlist guard already (the service guards once before dispatch),
     * but implementations that reach the network re-guard defensively.
     *
     * @param url    the target URL
     * @param config the effective fetch config (sidecar URL/token/timeout)
     * @return extracted attributes; empty (blank content) on any failure
     */
    TurFileAttributes fetch(URL url, TurUrlFetchConfig config);

    /** The concrete backend this fetcher serves ({@code SIMPLE} or {@code HEADLESS}). */
    TurUrlFetchMode getBackend();
}
