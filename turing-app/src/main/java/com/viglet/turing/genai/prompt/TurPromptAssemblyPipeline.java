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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.persona.TurPersonaPromptComposer;

/**
 * Block AL / §XXXV — the single-pass prompt assembler. Runs the ordered
 * {@link TurPromptContributor} list once and joins the emitted
 * {@link TurPromptSegment}s into the exact system-prompt string. Both the chat
 * runtime ({@code TurChatPromptAssembler}) and the Live Preview
 * ({@code TurSystemPromptPreviewService}) call this one method, so what an
 * operator inspects is byte-for-byte what the model receives — the root-cause fix
 * for the "two prompt-builders that drift" bug behind every Block AK symptom.
 *
 * <h2>Two ordering modes</h2>
 * <ul>
 *   <li><b>Legacy order</b> (default) — reproduces the historical
 *       {@link TurPersonaPromptComposer} layout <em>byte-identically</em>: the
 *       persona head (static + few-shot) wraps the body (agent-base + MCP +
 *       capability + skill + flow) with the {@code "\n\n----\n"} separator.
 *       {@code cacheBreakpointIndex = -1} — the STABLE prefix is interrupted by
 *       the PER_TURN few-shot block, so there is no clean cache boundary.</li>
 *   <li><b>Stable-first order</b> (T614, opt-in via
 *       {@code turing.prompt.assembly.stable-first-ordering}) — reorders every
 *       STABLE segment before every PER_TURN segment (relative order preserved
 *       within each group) and joins with a uniform {@code "\n\n"} so the heavy
 *       stable prefix (persona-static + agent-base + MCP + tool guidance) is
 *       contiguous and a provider prefix cache (T499/T502) can cache it. This
 *       changes what the model sees, so it is gated and must be re-validated —
 *       {@code cacheBreakpointIndex} is the count of leading STABLE segments.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurPromptAssemblyPipeline {

    private final List<TurPromptContributor> contributors;
    private final boolean stableFirstDefault;

    public TurPromptAssemblyPipeline(List<TurPromptContributor> contributors,
            @Value("${turing.prompt.assembly.stable-first-ordering:false}") boolean stableFirstDefault) {
        // Deterministic assembly order — lower order() runs earlier (closer to
        // the top of the prompt). Sorted once at construction.
        this.contributors = contributors.stream()
                .sorted(Comparator.comparingInt(TurPromptContributor::order))
                .toList();
        this.stableFirstDefault = stableFirstDefault;
    }

    /** Assemble using the configured default ordering mode. */
    public TurPromptAssembly assemble(TurPromptAssemblyContext context) {
        return assemble(context, stableFirstDefault);
    }

    /**
     * Assemble with an explicit ordering mode — used by tests and by the preview
     * so it can mirror whichever mode the runtime is configured for.
     *
     * @param stableFirst {@code true} for the T614 cache-friendly reorder;
     *                    {@code false} for the byte-identical legacy layout.
     */
    public TurPromptAssembly assemble(TurPromptAssemblyContext context, boolean stableFirst) {
        List<TurPromptSegment> raw = new ArrayList<>();
        for (TurPromptContributor contributor : contributors) {
            List<TurPromptSegment> emitted = contributor.contribute(context);
            if (emitted != null) {
                raw.addAll(emitted);
            }
        }
        return stableFirst ? assembleStableFirst(raw) : assembleLegacy(raw, context);
    }

    /**
     * Byte-identical reproduction of {@link TurPersonaPromptComposer#compose}: the
     * persona head wraps the body with {@code "\n\n----\n"} when a persona applies
     * and the body is non-blank.
     */
    private TurPromptAssembly assembleLegacy(List<TurPromptSegment> segments,
            TurPromptAssemblyContext context) {
        StringBuilder head = new StringBuilder();
        StringBuilder body = new StringBuilder();
        List<TurPromptSegment> ordered = new ArrayList<>(segments.size());
        List<TurPromptSegment> headSegs = new ArrayList<>();
        List<TurPromptSegment> bodySegs = new ArrayList<>();
        for (TurPromptSegment seg : segments) {
            if (seg.isPersonaHead()) {
                head.append(seg.text());
                headSegs.add(seg);
            } else {
                body.append(seg.text());
                bodySegs.add(seg);
            }
        }
        ordered.addAll(headSegs);
        ordered.addAll(bodySegs);
        String bodyText = body.toString();
        String systemText;
        if (context.persona() != null && StringUtils.hasText(bodyText)) {
            systemText = head + TurPersonaPromptComposer.PERSONA_BASE_SEPARATOR + bodyText;
        } else {
            systemText = head.toString() + bodyText;
        }
        return new TurPromptAssembly(List.copyOf(ordered), systemText, -1);
    }

    /**
     * T614 — STABLE segments first (in their relative order), then PER_TURN
     * segments, joined by a uniform blank-line separator. The breakpoint is the
     * number of leading STABLE segments: a provider prefix cache should cache up
     * to (and including) segment {@code breakpoint - 1}.
     */
    private TurPromptAssembly assembleStableFirst(List<TurPromptSegment> segments) {
        List<TurPromptSegment> stable = new ArrayList<>();
        List<TurPromptSegment> perTurn = new ArrayList<>();
        for (TurPromptSegment seg : segments) {
            if (seg.stability() == TurPromptStability.STABLE) {
                stable.add(seg);
            } else {
                perTurn.add(seg);
            }
        }
        List<TurPromptSegment> ordered = new ArrayList<>(segments.size());
        ordered.addAll(stable);
        ordered.addAll(perTurn);
        String systemText = ordered.stream()
                .map(TurPromptSegment::displayText)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        return new TurPromptAssembly(List.copyOf(ordered), systemText, stable.size());
    }
}
