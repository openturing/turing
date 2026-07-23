/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * T743 / T747 / §XLIX — per-request cross-cutting gateway behaviour selected by
 * headers, stackable on any {@code model}:
 * <ul>
 *   <li>{@code x-turing-cache: semantic} → {@link #semanticCache}</li>
 *   <li>{@code x-turing-guardrails: strict} → {@link #guardrails}</li>
 *   <li>{@code x-turing-rag-site: <site>} → {@link #ragSite} (RAG-as-a-header)</li>
 *   <li>{@code x-turing-tools: web_search,web_fetch} → {@link #nativeTools}
 *       (F.15 provider-native tools, normalised function ids)</li>
 *   <li>{@code x-turing-copilot-site: <site>} → {@link #copilotSite} (T795 — invoke
 *       the Vectorless (Structured-Data) RAG copilot for that site; the header form
 *       of the {@code turing-copilot:<site>} model route)</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurGatewayOptions(boolean semanticCache, boolean guardrails, String ragSite,
        Set<String> nativeTools, String copilotSite) {

    private static final TurGatewayOptions NONE = new TurGatewayOptions(false, false, null, Set.of(), null);

    /**
     * Backward-compatible 4-arg constructor (no {@code copilotSite}), so existing
     * callers/tests that predate T795 keep compiling.
     */
    public TurGatewayOptions(boolean semanticCache, boolean guardrails, String ragSite,
            Set<String> nativeTools) {
        this(semanticCache, guardrails, ragSite, nativeTools, null);
    }

    public static TurGatewayOptions none() {
        return NONE;
    }

    /** Builds options from the pre-T795 gateway headers (no copilot header). */
    public static TurGatewayOptions from(String cacheHeader, String guardrailsHeader,
            String ragSiteHeader, String toolsHeader) {
        return from(cacheHeader, guardrailsHeader, ragSiteHeader, toolsHeader, null);
    }

    /** Builds options from the gateway headers ({@code null}/other values = off). */
    public static TurGatewayOptions from(String cacheHeader, String guardrailsHeader,
            String ragSiteHeader, String toolsHeader, String copilotSiteHeader) {
        boolean cache = "semantic".equalsIgnoreCase(trim(cacheHeader));
        boolean guard = "strict".equalsIgnoreCase(trim(guardrailsHeader));
        String ragSite = trim(ragSiteHeader);
        if (ragSite != null && ragSite.isEmpty()) {
            ragSite = null;
        }
        String copilotSite = trim(copilotSiteHeader);
        if (copilotSite != null && copilotSite.isEmpty()) {
            copilotSite = null;
        }
        Set<String> tools = parseTools(toolsHeader);
        return (cache || guard || ragSite != null || copilotSite != null || !tools.isEmpty())
                ? new TurGatewayOptions(cache, guard, ragSite, tools, copilotSite) : NONE;
    }

    private static Set<String> parseTools(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : Arrays.asList(header.split(","))) {
            String fn = TurGatewayNativeToolsService.normaliseFunction(token);
            if (fn != null) {
                out.add(fn);
            }
        }
        return out;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
