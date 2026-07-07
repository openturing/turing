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
 * Block AK / T612 — the "replay a real turn" envelope on a {@link
 * TurSystemPromptPreviewDto}. When present, the preview was not synthesized from
 * a plain/simulated entry turn: it was rebuilt from a <em>real</em>
 * conversation's persisted state (its leaf flow cursor + collected slots + the
 * persona the runtime resolved), fed through the same shared assembler, so the
 * {@code assembledText} is what the model would receive for that conversation's
 * next turn — the ground truth for debugging a specific reply.
 *
 * <p>It also carries the conversation's T427 tool-call trace so the operator can
 * cross-read "what the prompt said" against "what the model actually called".
 *
 * @param conversationId the conversation the preview was replayed from.
 * @param resolved       {@code true} when a persisted state for this conversation
 *                       was found and drove the replay; {@code false} when the id
 *                       had no state (the preview fell back to a plain entry turn).
 * @param flowName       the flow governing the conversation's current cursor, or
 *                       {@code null} when none governs (or it isn't this agent's).
 * @param nodeId         the real cursor node the addendum was rebuilt at, or
 *                       {@code null} when no flow governs.
 * @param personaName    the persona the runtime resolved for the conversation
 *                       (honouring a {@code __activePersonaId} flow override), or
 *                       {@code null} when the agent has no persona.
 * @param toolCalls      the conversation's recorded tool calls, oldest first;
 *                       empty when none were traced (or the trace was evicted).
 * @param turnIndex      T618 — the captured turn being shown verbatim, or
 *                       {@code null} for a T612 current-state replay.
 * @param verbatim       T618 — {@code true} when {@code assembledText}/{@code
 *                       messages} were loaded byte-for-byte from a captured past
 *                       turn (not reconstructed from the conversation's current
 *                       state); the segments are then a single opaque block.
 * @param capturedAt     T618 — epoch millis the shown turn was captured, or
 *                       {@code 0} when not applicable / unknown.
 * @param availableTurns T618 — the captured turns available for this conversation
 *                       (newest first), for the preview's turn picker; empty when
 *                       the agent never opted into capture or none were stored.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSystemPromptReplayDto(
        String conversationId,
        boolean resolved,
        String flowName,
        String nodeId,
        String personaName,
        List<TurSystemPromptToolCallDto> toolCalls,
        Integer turnIndex,
        boolean verbatim,
        long capturedAt,
        List<TurSystemPromptCapturedTurnDto> availableTurns) {

    public TurSystemPromptReplayDto {
        toolCalls = toolCalls == null ? List.of() : toolCalls;
        availableTurns = availableTurns == null ? List.of() : availableTurns;
    }

    /** Back-compat convenience for the T612 6-arg call sites (no captured-turn picker). */
    public TurSystemPromptReplayDto(String conversationId, boolean resolved, String flowName,
            String nodeId, String personaName, List<TurSystemPromptToolCallDto> toolCalls) {
        this(conversationId, resolved, flowName, nodeId, personaName, toolCalls,
                null, false, 0L, List.of());
    }
}
