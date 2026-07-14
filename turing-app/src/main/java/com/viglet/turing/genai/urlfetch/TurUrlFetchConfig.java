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

/**
 * T739 / §XLVIII — the effective URL content-fetch configuration for the current
 * request, merged from {@code turing.url-fetch.*} env props (which win when set)
 * over the DB Global Settings row, produced by {@link TurUrlFetchConfigResolver}.
 *
 * <p>{@code browserlessUrl}/{@code browserlessToken} may be blank — a
 * {@code HEADLESS}/{@code AUTO} config with no sidecar then degrades to the
 * {@code SIMPLE} path (fail-open), so ingestion never breaks because a browser is
 * unavailable.
 *
 * @param mode             the active fetch mode
 * @param browserlessUrl   base URL of the browserless sidecar, or {@code ""}
 * @param browserlessToken decrypted browserless token, or {@code ""}
 * @param timeoutSeconds   per-request headless render timeout (≥ 1)
 * @param autoMinChars     in AUTO, min chars from SIMPLE before escalating (≥ 0)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurUrlFetchConfig(
        TurUrlFetchMode mode,
        String browserlessUrl,
        String browserlessToken,
        int timeoutSeconds,
        int autoMinChars) {

    /** True when a browserless sidecar base URL is configured. */
    public boolean hasBrowserless() {
        return browserlessUrl != null && !browserlessUrl.isBlank();
    }
}
