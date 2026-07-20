/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

/**
 * T743 / §XLIX — per-request cross-cutting gateway behaviour selected by headers,
 * stackable on any {@code model}:
 * <ul>
 *   <li>{@code x-turing-cache: semantic} → {@link #semanticCache}</li>
 *   <li>{@code x-turing-guardrails: strict} → {@link #guardrails}</li>
 *   <li>{@code x-turing-rag-site: <site>} → {@link #ragSite} (RAG-as-a-header)</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurGatewayOptions(boolean semanticCache, boolean guardrails, String ragSite) {

    private static final TurGatewayOptions NONE = new TurGatewayOptions(false, false, null);

    public static TurGatewayOptions none() {
        return NONE;
    }

    /** Builds options from the gateway headers ({@code null}/other values = off). */
    public static TurGatewayOptions from(String cacheHeader, String guardrailsHeader, String ragSiteHeader) {
        boolean cache = "semantic".equalsIgnoreCase(trim(cacheHeader));
        boolean guard = "strict".equalsIgnoreCase(trim(guardrailsHeader));
        String ragSite = trim(ragSiteHeader);
        if (ragSite != null && ragSite.isEmpty()) {
            ragSite = null;
        }
        return (cache || guard || ragSite != null)
                ? new TurGatewayOptions(cache, guard, ragSite) : NONE;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
