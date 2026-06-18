/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.spectator;

/**
 * One message of a live conversation, broadcast on the
 * {@link TurChatMessageEventBus} so spectating operators (T120) see the chat
 * fill in turn-by-turn over SSE. The analogue of
 * {@link com.viglet.turing.genai.workspace.TurWorkspaceEvent} for the chat
 * transcript itself: where the slot bus carries the merged variable map and the
 * workspace bus carries blob metadata, this bus carries the role-tagged text of
 * each completed turn.
 *
 * <p>Emitted from the single post-turn telemetry seam
 * ({@code TurChatStreamingDispatcher.recordTurnTelemetry}) for normal
 * LLM-driven turns ({@code manual=false}) and from
 * {@link TurCopilotService#manualTurn} when an operator takes the wheel
 * ({@code manual=true}). The {@code type} mirrors the chat SSE discriminator
 * ({@code user} / {@code assistant}); {@code manual} lets the spectator UI badge
 * operator-authored replies distinctly from model output.
 *
 * @param conversationId opaque session key (the {@code TUR_SESSION} cookie value)
 * @param role           {@code "user"} or {@code "assistant"}
 * @param content        the message text
 * @param manual         {@code true} when an operator authored this turn (co-pilot)
 * @param epochMillis    server timestamp of the emission (ordering aid for the UI)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurChatMessageEvent(String conversationId, String role, String content,
        boolean manual, long epochMillis) {

    /** Role token for a visitor message. */
    public static final String ROLE_USER = "user";
    /** Role token for an assistant (model or operator) message. */
    public static final String ROLE_ASSISTANT = "assistant";

    /** A visitor message — never operator-authored. */
    public static TurChatMessageEvent user(String conversationId, String content) {
        return new TurChatMessageEvent(conversationId, ROLE_USER, content, false,
                System.currentTimeMillis());
    }

    /** An assistant message produced by the model (not the operator). */
    public static TurChatMessageEvent assistant(String conversationId, String content) {
        return new TurChatMessageEvent(conversationId, ROLE_ASSISTANT, content, false,
                System.currentTimeMillis());
    }

    /** An assistant message an operator typed while co-piloting (T120). */
    public static TurChatMessageEvent assistantManual(String conversationId, String content) {
        return new TurChatMessageEvent(conversationId, ROLE_ASSISTANT, content, true,
                System.currentTimeMillis());
    }
}
