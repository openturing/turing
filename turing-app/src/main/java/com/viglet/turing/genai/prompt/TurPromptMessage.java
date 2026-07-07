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
 * Block AL / §XXXV.3 (T616) — one labeled message a {@link TurMessageContributor}
 * prepends to the turn's history, ahead of the client-supplied recent window.
 *
 * <p>Where a {@link TurPromptSegment} is a fragment of the single <em>system</em>
 * message, a {@code TurPromptMessage} is a whole conversational turn
 * ({@code user}/{@code assistant}) synthesized from a source that lives
 * <em>outside</em> the system prompt — today the T115 chat-memory summary and the
 * T30 relevance-retrieved older turns. The assembler fans these into Spring AI
 * {@code UserMessage}/{@code AssistantMessage}s through the same single path the
 * client history uses, so the whole message list has one assembly point (and the
 * Live Preview can mirror it with provenance from {@link #origin()} /
 * {@link #label()}).
 *
 * @param role    {@code "user"} or {@code "assistant"} — drives the Spring AI
 *                message type the assembler builds.
 * @param content the literal message body (already marked up by the source, e.g.
 *                the {@code [earlier turn #4]} / {@code [Summary of earlier
 *                conversation]} prefixes).
 * @param origin  stable machine-readable source bucket the preview themes on —
 *                one of the {@code ORIGIN_*} constants.
 * @param label   human-readable name for the message (or {@code null} when the
 *                origin label is enough).
 * @param tokens  best-effort {@code chars/4} token estimate for {@code content}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptMessage(
        String role,
        String content,
        String origin,
        String label,
        int tokens) {

    /** T115 chat-memory compression summary block. */
    public static final String ORIGIN_MEMORY_SUMMARY = "MEMORY_SUMMARY";
    /** T30 BM25 relevance-retrieved older turns. */
    public static final String ORIGIN_MEMORY_RELEVANCE = "MEMORY_RELEVANCE";

    /**
     * Factory that computes the token estimate from {@code content} so callers
     * never pass it inconsistently — same {@code chars/4} heuristic as
     * {@link TurPromptSegment#estimateTokens(String)}.
     */
    public static TurPromptMessage of(String role, String content, String origin, String label) {
        return new TurPromptMessage(role, content, origin, label,
                TurPromptSegment.estimateTokens(content));
    }
}
