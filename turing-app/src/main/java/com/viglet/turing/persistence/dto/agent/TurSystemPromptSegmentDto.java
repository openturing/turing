/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

/**
 * One labeled fragment of the final system prompt, surfaced by the AI Agent
 * "System Prompt" page Live Preview so an operator can see exactly which text
 * comes from where. The runtime assembler concatenates several sources
 * ({@code agent.systemPrompt}, MCP server instructions, the active chat-flow
 * node addendum, the persona block) into a single system message — this DTO
 * makes that opaque concatenation legible.
 *
 * @param origin     stable machine-readable source bucket the frontend themes
 *                   on: {@code PERSONA}, {@code AGENT}, {@code MCP},
 *                   {@code FLOW}, {@code FEW_SHOT}, {@code RAG}.
 * @param title      human-readable heading for the segment (localized client-side
 *                   only for the origin label; this carries the concrete name,
 *                   e.g. the persona or MCP server title).
 * @param content    the literal text this segment contributes to the prompt.
 *                   Empty when {@code included} is false (informational segment).
 * @param included   {@code true} when this segment is part of the prompt for a
 *                   plain turn (no flow, default persona, no RAG override);
 *                   {@code false} for informational/runtime-only segments.
 * @param runtimeOnly {@code true} when the segment only materializes at runtime
 *                   and depends on conversation state (active flow node,
 *                   few-shot retrieval keyed by the user message, SN-site RAG
 *                   override) — shown as an example, not as fixed text.
 * @param note       optional one-line explanation of when/why this segment
 *                   applies; {@code null} when self-explanatory.
 * @param tokens     Block AL / T613 — best-effort {@code chars/4} token estimate
 *                   for this segment's contributed text (0 for informational
 *                   runtime-only segments); provider-approximate, never a budget
 *                   gate.
 * @param stability  Block AL / T613 — {@code "STABLE"} (cacheable prefix) or
 *                   {@code "PER_TURN"} (varies per turn); {@code null} for
 *                   informational segments.
 * @param inertRegions Block AK / T609 — spans of this segment that are inert on
 *                   the previewed turn (a flow neutralizes them); empty when none.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSystemPromptSegmentDto(
        String origin,
        String title,
        String content,
        boolean included,
        boolean runtimeOnly,
        String note,
        int tokens,
        String stability,
        java.util.List<TurSystemPromptInertRegionDto> inertRegions) {

    public TurSystemPromptSegmentDto {
        inertRegions = inertRegions == null ? java.util.List.of() : inertRegions;
    }

    /** Back-compat convenience for the pre-Block-AL 6-arg call sites. */
    public TurSystemPromptSegmentDto(String origin, String title, String content,
            boolean included, boolean runtimeOnly, String note) {
        this(origin, title, content, included, runtimeOnly, note, 0, null, java.util.List.of());
    }

    /** Back-compat convenience for the T613 8-arg call sites (no inert regions). */
    public TurSystemPromptSegmentDto(String origin, String title, String content,
            boolean included, boolean runtimeOnly, String note, int tokens, String stability) {
        this(origin, title, content, included, runtimeOnly, note, tokens, stability, java.util.List.of());
    }
}
