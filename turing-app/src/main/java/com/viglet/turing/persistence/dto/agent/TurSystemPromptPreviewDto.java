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

import java.util.List;

/**
 * Live Preview payload for an AI Agent's system prompt. Breaks the runtime
 * concatenation into labeled {@link TurSystemPromptSegmentDto segments} (with
 * provenance), lists the {@link TurSystemPromptToolDto tools} that ship as a
 * separate schema channel, and exposes {@code assembledText}: the realistic
 * system message for a plain turn (default persona, no governing flow, no
 * SN-site RAG override) so the operator can read the final prompt verbatim.
 *
 * @param segments       ordered fragments, in the same order the runtime emits
 *                       them (persona block first, then the agent base prompt,
 *                       MCP instructions, and the selected flow's addendum).
 * @param tools          tools available to the LLM (sent as a schema channel).
 * @param assembledText  the final system message for the previewed scenario
 *                       (default persona, the {@code selectedFlowId} flow
 *                       governing at its entry node, no SN-site RAG override).
 * @param flows          every chat flow on the agent, for the preview's flow
 *                       selector.
 * @param selectedFlowId the flow whose addendum is folded into this preview, or
 *                       {@code null} when previewing a no-flow turn.
 * @param totalTokens    Block AL / T613 — best-effort total {@code chars/4} token
 *                       estimate across the assembled (non-informational) segments.
 * @param stableTokens   Block AL / T614 — token estimate of the STABLE
 *                       (cacheable-prefix) segments; the share a provider prefix
 *                       cache could reuse across turns.
 * @param cacheBreakpointIndex Block AL / T614 — segment index at which the
 *                       PER_TURN tail begins under cache-optimized ordering, or
 *                       {@code -1} in legacy order (STABLE prefix not contiguous).
 * @param estimatedCostUsd Block AK / T608 — best-effort USD cost of sending this
 *                       assembled system prompt once (input tokens only) at the
 *                       agent's first enabled LLM instance's configured price;
 *                       {@code 0.0} when the model is unpriced or local.
 * @param costModelName  Block AK / T608 — the model the cost estimate is based
 *                       on (the resolved LLM instance's model), or {@code null}
 *                       when no instance/price could be resolved.
 * @param flowNodes      Block AK / T611 — the selected flow's nodes, for the
 *                       preview's node picker; empty when no flow governs.
 * @param selectedNodeId Block AK / T611 — the flow node this preview rendered
 *                       (the picked node, or the resolved entry anchor), or
 *                       {@code null} when no flow governs.
 * @param messages       Block AL / T617 — the history-prefix messages the runtime
 *                       {@code TurMessageAssemblyPipeline} prepends to the client
 *                       history (T115 memory summary + T30 relevance-retrieved
 *                       turns), so the operator inspects the whole message list,
 *                       not just the system message. Empty when the agent has no
 *                       memory sources enabled.
 * @param replay         Block AK / T612 — non-{@code null} when this preview was
 *                       replayed from a real conversation's persisted state (the
 *                       ground-truth prompt for that turn) rather than synthesized;
 *                       carries the source conversation + its tool-call trace.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSystemPromptPreviewDto(
        List<TurSystemPromptSegmentDto> segments,
        List<TurSystemPromptToolDto> tools,
        String assembledText,
        List<TurSystemPromptFlowOptionDto> flows,
        String selectedFlowId,
        int totalTokens,
        int stableTokens,
        int cacheBreakpointIndex,
        double estimatedCostUsd,
        String costModelName,
        List<TurSystemPromptFlowNodeDto> flowNodes,
        String selectedNodeId,
        List<TurSystemPromptMessageDto> messages,
        TurSystemPromptReplayDto replay) {

    public TurSystemPromptPreviewDto {
        flowNodes = flowNodes == null ? List.of() : flowNodes;
        messages = messages == null ? List.of() : messages;
    }

    /** Back-compat convenience for the T617 13-arg call sites (no replay envelope). */
    public TurSystemPromptPreviewDto(List<TurSystemPromptSegmentDto> segments,
            List<TurSystemPromptToolDto> tools, String assembledText,
            List<TurSystemPromptFlowOptionDto> flows, String selectedFlowId,
            int totalTokens, int stableTokens, int cacheBreakpointIndex,
            double estimatedCostUsd, String costModelName,
            List<TurSystemPromptFlowNodeDto> flowNodes, String selectedNodeId,
            List<TurSystemPromptMessageDto> messages) {
        this(segments, tools, assembledText, flows, selectedFlowId,
                totalTokens, stableTokens, cacheBreakpointIndex, estimatedCostUsd, costModelName,
                flowNodes, selectedNodeId, messages, null);
    }

    /** Back-compat convenience for the T611 12-arg call sites (no message list). */
    public TurSystemPromptPreviewDto(List<TurSystemPromptSegmentDto> segments,
            List<TurSystemPromptToolDto> tools, String assembledText,
            List<TurSystemPromptFlowOptionDto> flows, String selectedFlowId,
            int totalTokens, int stableTokens, int cacheBreakpointIndex,
            double estimatedCostUsd, String costModelName,
            List<TurSystemPromptFlowNodeDto> flowNodes, String selectedNodeId) {
        this(segments, tools, assembledText, flows, selectedFlowId,
                totalTokens, stableTokens, cacheBreakpointIndex, estimatedCostUsd, costModelName,
                flowNodes, selectedNodeId, List.of());
    }

    /** Back-compat convenience for the pre-Block-AL 5-arg call sites. */
    public TurSystemPromptPreviewDto(List<TurSystemPromptSegmentDto> segments,
            List<TurSystemPromptToolDto> tools, String assembledText,
            List<TurSystemPromptFlowOptionDto> flows, String selectedFlowId) {
        this(segments, tools, assembledText, flows, selectedFlowId, 0, 0, -1);
    }

    /** Back-compat convenience for the T613/T614 8-arg call sites (no cost). */
    public TurSystemPromptPreviewDto(List<TurSystemPromptSegmentDto> segments,
            List<TurSystemPromptToolDto> tools, String assembledText,
            List<TurSystemPromptFlowOptionDto> flows, String selectedFlowId,
            int totalTokens, int stableTokens, int cacheBreakpointIndex) {
        this(segments, tools, assembledText, flows, selectedFlowId,
                totalTokens, stableTokens, cacheBreakpointIndex, 0.0, null);
    }

    /** Back-compat convenience for the T608 10-arg call sites (no node picker). */
    public TurSystemPromptPreviewDto(List<TurSystemPromptSegmentDto> segments,
            List<TurSystemPromptToolDto> tools, String assembledText,
            List<TurSystemPromptFlowOptionDto> flows, String selectedFlowId,
            int totalTokens, int stableTokens, int cacheBreakpointIndex,
            double estimatedCostUsd, String costModelName) {
        this(segments, tools, assembledText, flows, selectedFlowId,
                totalTokens, stableTokens, cacheBreakpointIndex, estimatedCostUsd, costModelName,
                List.of(), null);
    }
}
