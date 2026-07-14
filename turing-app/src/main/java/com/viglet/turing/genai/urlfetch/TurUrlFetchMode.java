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
 * T739 / §XLVIII — how the platform turns a content URL into text.
 *
 * <ul>
 *   <li>{@link #SIMPLE} — the legacy path: a plain {@code HttpURLConnection} GET
 *       whose raw response is parsed by Tika. Cheap and dependency-free, but a
 *       JS-rendered SPA yields an empty shell and extracts no body text.</li>
 *   <li>{@link #HEADLESS} — always render the page in the {@code browserless}
 *       sidecar first, then parse the rendered HTML with Tika. Handles SPAs at
 *       the cost of a browser round-trip.</li>
 *   <li>{@link #AUTO} — run {@code SIMPLE} first and escalate to {@code HEADLESS}
 *       only when the extracted text is blank or below a small threshold. Static
 *       pages stay free; SPAs transparently get rendered. The recommended,
 *       scalable default once a sidecar is configured.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurUrlFetchMode {

    SIMPLE,
    HEADLESS,
    AUTO;

    /** The shipped default: today's behavior, no browser dependency. */
    public static final TurUrlFetchMode DEFAULT = SIMPLE;

    /**
     * Parse a stored/config value into a mode, falling back to {@link #DEFAULT}
     * for {@code null}, blank or unrecognized input so a corrupt row can never
     * break ingestion.
     */
    public static TurUrlFetchMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
