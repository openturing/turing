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
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSystemPromptPreviewDto(
        List<TurSystemPromptSegmentDto> segments,
        List<TurSystemPromptToolDto> tools,
        String assembledText,
        List<TurSystemPromptFlowOptionDto> flows,
        String selectedFlowId) {
}
