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

/**
 * Block AL / §XXXV.1 — one labeled fragment of the turn's system prompt, emitted
 * by a {@link TurPromptContributor}. The assembler concatenates the segments'
 * {@link #text()} (in order, with the persona wrap applied by
 * {@link TurPromptAssemblyPipeline}) to produce the exact system message the LLM
 * receives — so provenance, per-segment token accounting and the STABLE /
 * PER_TURN split are produced <em>at the source</em> instead of reverse-engineered
 * from the finished string (which was the root cause behind every Block AK
 * symptom: two prompt-builders that drifted).
 *
 * <p>{@code text} is the <b>exact</b> contribution to the assembled prompt,
 * including any leading separator the legacy concatenation baked in (e.g. the
 * {@code "\n\n"} that opens the MCP / skill / flow blocks). This is what keeps a
 * full contributor pipeline byte-identical to the legacy
 * {@code TurPersonaPromptComposer} output. Use {@link #displayText()} for a
 * human-facing rendering that trims that leading whitespace.
 *
 * @param origin     stable machine-readable source bucket the preview themes on
 *                   — one of the {@code ORIGIN_*} constants.
 * @param title      human-readable name for the segment (persona / MCP server
 *                   name, flow + node), or {@code null} when the origin label is
 *                   enough.
 * @param text       the literal, byte-exact text this segment contributes to the
 *                   assembled prompt (may carry a leading separator).
 * @param stability  {@link TurPromptStability#STABLE} vs
 *                   {@link TurPromptStability#PER_TURN}.
 * @param tokens     best-effort {@code chars/4} token estimate for {@code text}
 *                   (provider-approximate — never used for a hard budget gate).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptSegment(
        String origin,
        String title,
        String text,
        TurPromptStability stability,
        int tokens) {

    public static final String ORIGIN_PERSONA = "PERSONA";
    public static final String ORIGIN_FEW_SHOT = "FEW_SHOT";
    /** T718 — persona grounded-knowledge block (retrieved proprietary content). */
    public static final String ORIGIN_GROUNDING = "GROUNDING";
    public static final String ORIGIN_AGENT = "AGENT";
    public static final String ORIGIN_MCP = "MCP";
    public static final String ORIGIN_CAPABILITY = "CAPABILITY";
    public static final String ORIGIN_SKILL = "SKILL";
    public static final String ORIGIN_FLOW = "FLOW";

    /**
     * Factory that computes the token estimate from {@code text} so callers
     * never pass it inconsistently. See {@link #estimateTokens(String)}.
     */
    public static TurPromptSegment of(String origin, String title, String text,
            TurPromptStability stability) {
        return new TurPromptSegment(origin, title, text, stability, estimateTokens(text));
    }

    /**
     * The persona-family segments ({@link #ORIGIN_PERSONA} + {@link #ORIGIN_FEW_SHOT})
     * form the prompt "head" that the legacy composer wraps around the body with
     * a {@code "\n\n----\n"} separator. The pipeline uses this to reproduce that
     * wrap exactly.
     */
    public boolean isPersonaHead() {
        return ORIGIN_PERSONA.equals(origin) || ORIGIN_FEW_SHOT.equals(origin);
    }

    /** {@code text} with any leading blank lines trimmed — for legible display. */
    public String displayText() {
        if (text == null) {
            return "";
        }
        return text.stripLeading();
    }

    /**
     * {@code chars/4} with a ceiling so a short block still counts as ≥1 token —
     * the same heuristic {@code TurTokenBudgetService} uses, applied per segment.
     * Best-effort and provider-approximate by design (§XXXIV invariant).
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (text.length() + 3L) / 4L);
    }
}
