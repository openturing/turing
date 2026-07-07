/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.servicetier;

import java.util.Locale;
import java.util.Optional;

/**
 * F.7 / §X.8.e — the latency/price tier a synchronous LLM request runs at.
 *
 * <p>Vendor-neutral: OpenAI exposes {@code auto / default / flex / priority} on
 * the Responses API, while Anthropic only distinguishes {@code auto} (use the
 * priority tier when available) from {@code standard_only}. This enum holds the
 * canonical OpenAI-style value and maps onto each vendor's request enum via
 * {@link #openAiValue()} / {@link #anthropicValue()} so the request builders
 * stay SDK-agnostic at the call site.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurServiceTier {

    /** Let the provider pick — priority when the account has it, else standard. */
    AUTO("auto"),
    /** The standard tier explicitly. */
    DEFAULT("default"),
    /** Cheaper, higher-latency near-batch tier (OpenAI {@code flex}). */
    FLEX("flex"),
    /** Lowest-latency interactive tier (OpenAI {@code priority}). */
    PRIORITY("priority");

    private final String value;

    TurServiceTier(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** The OpenAI Responses {@code service_tier} string. */
    public String openAiValue() {
        return value;
    }

    /**
     * The Anthropic Messages {@code service_tier} string. Anthropic only has
     * {@code auto} / {@code standard_only}: AUTO and PRIORITY map to
     * {@code auto} (allow the priority tier), DEFAULT and FLEX to
     * {@code standard_only}.
     */
    public String anthropicValue() {
        return (this == AUTO || this == PRIORITY) ? "auto" : "standard_only";
    }

    public static Optional<TurServiceTier> fromOption(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (TurServiceTier tier : values()) {
            if (tier.value.equals(normalized)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
