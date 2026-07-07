/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.resilience.llm;

import java.util.Locale;

/**
 * T518 / §XXVIII.14 — how the cost-aware meta-provider orders the candidates in
 * a fallback chain.
 *
 * <ul>
 *   <li>{@link #PRIORITY} — try candidates in the configured order (the first is
 *       primary, the rest are failover). The classic resilience posture.</li>
 *   <li>{@link #CHEAPEST} — order candidates by price ascending (cheapest-capable
 *       routing): the least-expensive instance serves, the dearer ones are
 *       failover. Uses the F.13 / T289 per-model pricing.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurLlmFallbackMode {
    PRIORITY,
    CHEAPEST;

    /** Lenient parse; unknown/blank/null falls back to {@link #PRIORITY}. */
    public static TurLlmFallbackMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PRIORITY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PRIORITY;
        }
    }
}
