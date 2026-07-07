/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.prompt;

import java.util.List;

/**
 * Block AL / §XXXV — the result of running the contributor pipeline once: the
 * ordered {@link TurPromptSegment}s and the assembled system-prompt text they
 * join to. Both the runtime (which turns {@link #systemText()} into the system
 * {@code Message}) and the Live Preview (which renders {@link #segments()})
 * consume this single object — so what the operator inspects is exactly what the
 * model receives.
 *
 * @param segments            the segments in final assembly order (STABLE-first
 *                            when T614 ordering is on, else legacy order).
 * @param systemText          the exact assembled system-prompt string.
 * @param cacheBreakpointIndex T614 — the segment index at which the PER_TURN
 *                            (uncacheable) tail begins, i.e. the number of leading
 *                            STABLE segments. A provider prefix cache should set
 *                            its breakpoint at the end of segment
 *                            {@code cacheBreakpointIndex - 1}. {@code -1} when the
 *                            split is not meaningful (stable-first ordering off).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptAssembly(
        List<TurPromptSegment> segments,
        String systemText,
        int cacheBreakpointIndex) {

    /** Total best-effort token estimate across every segment. */
    public int totalTokens() {
        return segments.stream().mapToInt(TurPromptSegment::tokens).sum();
    }

    /** Sum of the token estimates of the leading STABLE (cacheable-prefix) segments. */
    public int stableTokens() {
        if (cacheBreakpointIndex < 0) {
            return segments.stream()
                    .filter(s -> s.stability() == TurPromptStability.STABLE)
                    .mapToInt(TurPromptSegment::tokens)
                    .sum();
        }
        return segments.subList(0, cacheBreakpointIndex).stream()
                .mapToInt(TurPromptSegment::tokens)
                .sum();
    }
}
